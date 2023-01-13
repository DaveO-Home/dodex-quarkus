package golf.handicap.db

import dmo.fs.db.handicap.DbConfiguration
import dmo.fs.utils.ColorUtilConstants
import golf.handicap.Score
import golf.handicap.db.rx.IPopulateScore
import golf.handicap.generated.tables.records.ScoresRecord
import golf.handicap.generated.tables.references.SCORES
import handicap.grpc.*
import io.smallrye.mutiny.Uni
import io.vertx.mutiny.core.Promise
import io.vertx.mutiny.sqlclient.PropertyKind
import io.vertx.mutiny.sqlclient.Tuple
import org.jooq.*
import org.jooq.impl.*
import org.jooq.impl.DSL.*
import java.sql.*
import java.util.*
import java.util.logging.Logger

class PopulateScore : SqlConstants(), IPopulateScore {
    companion object {
        private val LOGGER = Logger.getLogger(PopulateScore::class.java.name)
        private val regEx = "\\$\\d".toRegex()

        @Throws(SQLException::class)
        @JvmStatic
        fun buildSql() {
            GETSCOREINSERT =
                if (qmark) setupInsertScore().replace(regEx, "?")
                else setupInsertScore().replace("\"", "")

            GETSCOREBYTEETIME =
                if (qmark) setupSelectScore().replace(regEx, "?")
                else setupSelectScore().replace("\"", "")

            GETSCOREUPDATE =
                if (qmark) setupUpdateScore().replace(regEx, "?")
                else setupUpdateScore().replace("\"", "")

            GETGOLFERUPDATECHECKED =
                if (qmark) setupUpdateGolfer().replace(regEx, "?")
                else setupUpdateGolfer().replace("\"", "")
        }

//        init {}

        @JvmStatic
        private fun setupInsertScore(): String {
            val score: ScoresRecord = create!!.newRecord(SCORES)

            return create!!.renderNamedParams(
                insertInto(
                    SCORES,
                    SCORES.PIN,
                    SCORES.GROSS_SCORE,
                    SCORES.NET_SCORE,
                    SCORES.ADJUSTED_SCORE,
                    SCORES.TEE_TIME,
                    SCORES.HANDICAP,
                    SCORES.COURSE_SEQ,
                    SCORES.COURSE_TEES
                )
                    .values(
                        score.pin,
                        score.grossScore,
                        score.netScore,
                        score.adjustedScore,
                        score.teeTime,
                        score.handicap,
                        score.courseSeq,
                        score.courseTees
                    )
            )
        }

        @JvmStatic
        private fun setupSelectScore(): String {

            return create!!.renderNamedParams(
                select(
                    SCORES.PIN,
                    SCORES.GROSS_SCORE,
                    SCORES.NET_SCORE,
                    SCORES.ADJUSTED_SCORE,
                    SCORES.TEE_TIME,
                    SCORES.HANDICAP,
                    SCORES.COURSE_SEQ,
                    SCORES.COURSE_TEES
                )
                    .from(SCORES)
                    .where(SCORES.PIN.eq("$").and(SCORES.TEE_TIME.eq("$")).and(SCORES.COURSE_SEQ.eq(0)))
            )
        }

        @JvmStatic
        private fun setupUpdateScore(): String {

            return create!!.renderNamedParams(
                update(table("SCORES"))
                    .set(field("gross_score"), 0)
                    .set(field("adjusted_score"), 0)
                    .set(field("net_score"), 0)
                    .set(field("handicap"), 0)
                    .where(
                        field("PIN").eq("$").and(field("TEE_TIME").eq("$")).and(field("COURSE_SEQ").eq(0))
                    )
            )
        }

        @JvmStatic
        fun setupUpdateGolfer(): String {
            return create!!.renderNamedParams(
                update(table("GOLFER"))
                    .set(field("OVERLAP_YEARS"), "$")
                    .set(field("PUBLIC"), "$")
                    .where(field("pin").eq("$"))
            )
        }
    }

    private fun getScoreByTeetime(score: Score): Uni<MutableSet<Score>> {
        val promise: Promise<MutableSet<Score>> = Promise.promise()
        val scores = mutableSetOf<Score>()

        pool!!
            .connection
            .onItem().invoke { conn ->
                val sql = GETSCOREBYTEETIME
                val parameters: Tuple = Tuple.tuple()

                parameters.addString(score.golfer!!.pin)
                parameters.addString(score.teeTime)
                parameters.addInteger(score.course!!.course_key)

                conn.preparedQuery(sql)
                    .execute(parameters)
                    .onFailure().invoke { err ->
                        LOGGER.severe(
                            String.format("Error getting score for Tee Time: %s -- %s", err.message)
                        )
                    }
                    .onItem().invoke { rows ->
                        for (row in rows) {
                            val newScore = Score()
                            newScore.golfer = golf.handicap.Golfer()
                            newScore.course = golf.handicap.Course()
                            newScore.golfer!!.pin = row.getString(0) // PIN
                            newScore.grossScore = row.getInteger(1) // GROSS_SCORE
                            newScore.netScore = row.getFloat(2) // NET_SCORE
                            newScore.adjustedScore = row.getInteger(3) // ADJUSTED_SCORE
                            newScore.teeTime = row.getString(4) // TEE_TIME
                            newScore.handicap = row.getFloat(5) // HANDICAP
                            newScore.golfer!!.handicap = row.getFloat(5) // HANDICAP
                            newScore.course!!.course_key = row.getInteger(6) // COURSE_SEQ
                            newScore.course!!.teeId = row.getInteger(7) // COURSE_TEES
                            newScore.tees = row.getInteger(7).toString() // COURSE_TEES
                            scores.add(newScore)
                        }
                        score.status = rows.rowCount()
                    }
                    .onTermination().invoke { ->
                        promise.complete(scores)
                        conn.close().subscribeAsCompletionStage()
                    }
                    .subscribeAsCompletionStage()
            }
            .subscribeAsCompletionStage()

        return promise.future()
    }

    override fun setScore(
        score: Score
    ): Uni<Boolean> {
        val promise: Promise<Boolean> = Promise.promise()

        pool!!.withTransaction { conn ->
            val parameters: Tuple = Tuple.tuple()
            var isInserted = true

            getScoreByTeetime(score).onItem().invoke { scores ->
                if (scores.isEmpty()) {
                    parameters.addString(score.golfer!!.pin)
                    parameters.addInteger(score.grossScore)
                    parameters.addFloat(score.netScore)
                    parameters.addInteger(score.adjustedScore)
                    parameters.addString(score.teeTime)
                    parameters.addFloat(score.handicap)
                    parameters.addInteger(score.course!!.course_key)
                    parameters.addInteger(score.course!!.teeId)

                    val cf = conn.preparedQuery(GETSCOREINSERT)
                        .execute(parameters)
                        .onFailure().invoke { err ->
                            String.format(
                                "%sError Adding Golfer Score - %s%s %s %s %s",
                                ColorUtilConstants.RED,
                                err,
                                ColorUtilConstants.RESET,
                                err.stackTraceToString(),
                                parameters.deepToString(),
                                GETSCOREINSERT
                            )
                            conn.close().subscribeAsCompletionStage()
                            isInserted = false
                        }
                        .onItem().invoke { rows ->
                            if (DbConfiguration.isUsingMariadb()) {
                                score.scoreId = 0
                            } else if (DbConfiguration.isUsingSqlite3()) {
                                val generatedKey = PropertyKind.create(
                                    "generated-keys",
                                    io.vertx.sqlclient.Row::class.java
                                )
                                score.scoreId = rows.property(generatedKey).getInteger(0)
                            }
                            promise.complete(isInserted)
                            conn.close().subscribeAsCompletionStage()
                        }
                        .subscribeAsCompletionStage()
                    Uni.createFrom().item(cf)
                } else {
                    conn.close().subscribeAsCompletionStage()
                    updateScore(score).onItem().invoke { ->
                        updateGolfer(score).onItem().invoke { ->
                            promise.complete(isInserted)
                        }.subscribeAsCompletionStage()
                    }.subscribeAsCompletionStage()
                }
            }
        }.subscribeAsCompletionStage()

        return promise.future()
    }

    private fun updateScore(score: Score): Uni<Score> {
        val promise: Promise<Score> = Promise.promise()

        pool!!.withTransaction { conn ->
            val parameters: Tuple = Tuple.tuple()

            parameters.addInteger(score.grossScore)
            parameters.addInteger(score.adjustedScore)
            parameters.addFloat(score.netScore)
            parameters.addFloat(score.handicap)
            parameters.addString(score.golfer!!.pin)
            parameters.addString(score.teeTime)
            parameters.addInteger(score.course!!.course_key)

            val cf = conn.preparedQuery(GETSCOREUPDATE)
                .execute(parameters)
                .onFailure().invoke { err ->
                    LOGGER.severe(
                        String.format("Error getting score for Tee Time: %s", err.message)
                    )
                }
                .onItem().invoke { rows ->
                    score.status = rows.rowCount()
                }
                .onTermination().invoke { ->
                    promise.complete(score)
                    conn.close().subscribeAsCompletionStage()
                }
                .subscribeAsCompletionStage()
            Uni.createFrom().item(cf)
        }.subscribeAsCompletionStage()

        return promise.future()
    }

    private fun updateGolfer(score: Score): Uni<Score> {
        val promise: Promise<Score> = Promise.promise()

        pool!!.withTransaction { conn ->
            val parameters: Tuple = Tuple.tuple()

            parameters
                .addBoolean(score.golfer!!.overlap)
                .addBoolean(score.golfer!!.public)
                .addString(score.golfer!!.pin)
            val cf = conn.preparedQuery(GETGOLFERUPDATECHECKED)
                .execute(parameters)
                .onItem().invoke { _ ->
                    score.golfer!!.message = "Golfer public/overlap updated"
                }
                .onFailure().invoke { err: Throwable ->
                    LOGGER.severe(
                        String.format(
                            "%sError updating golfer Score: %s%s",
                            ColorUtilConstants.RED,
                            err,
                            ColorUtilConstants.RESET,
                        )
                    )
                    score.status = -1
                    score.golfer!!.message = "Golfer update failed"
                }
                .onTermination().invoke { ->
                    promise.complete(score)
                    conn.close().subscribeAsCompletionStage()
                }
                .subscribeAsCompletionStage()
            Uni.createFrom().item(cf)
        }.subscribeAsCompletionStage()

        return promise.future()
    }
}
