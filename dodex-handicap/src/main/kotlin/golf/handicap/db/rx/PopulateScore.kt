package golf.handicap.db.rx

import dmo.fs.db.wsnext.DbConfiguration
import dmo.fs.utils.ColorUtilConstants
import golf.handicap.generated.tables.records.ScoresRecord
import golf.handicap.generated.tables.references.SCORES
import handicap.grpc.*
import io.smallrye.mutiny.Uni
import io.vertx.core.Future
import io.vertx.reactivex.core.Promise
import io.vertx.rxjava3.jdbcclient.JDBCPool
import io.vertx.rxjava3.sqlclient.Tuple
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

    fun getScoreByTeetime(score: golf.handicap.Score): Future<MutableSet<golf.handicap.Score>> {
        val promise: Promise<MutableSet<golf.handicap.Score>> = Promise.promise()
        val scores = mutableSetOf<golf.handicap.Score>()

        pool!!
            .rxGetConnection()
            .doOnSuccess { conn ->
                val sql = GETSCOREBYTEETIME
                val parameters: Tuple = Tuple.tuple()

                parameters.addString(score.golfer!!.pin)
                parameters.addString(score.teeTime)
                parameters.addInteger(score.course!!.course_key)

                conn.preparedQuery(sql)
                    .rxExecute(parameters)
                    .doOnError { err ->
                        LOGGER.severe(
                            String.format("Error getting score for Tee Time: %s -- %s", err.message)
                        )
                        promise.complete(scores)
                    }
                    .doOnSuccess { rows ->
                        for (row in rows) {
                            val newScore = golf.handicap.Score()
                            newScore.golfer = golf.handicap.Golfer()
                            newScore.course = golf.handicap.Course()
                            newScore.golfer!!.pin = row.getString(0) // "PIN")
                            newScore.grossScore = row.getInteger(1) // "GROSS_SCORE")
                            newScore.netScore = row.getFloat(2) // "NET_SCORE")
                            newScore.adjustedScore = row.getInteger(3) // "ADJUSTED_SCORE")
                            newScore.teeTime = row.getString(4) // "TEE_TIME")
                            newScore.handicap = row.getFloat(5) // "HANDICAP")
                            newScore.golfer!!.handicap = row.getDouble(5) // "HANDICAP")
                            newScore.course!!.course_key = row.getInteger(6) // "COURSE_SEQ")
                            newScore.course!!.teeId = row.getInteger(7) // "COURSE_TEES")
                            newScore.tees = row.getInteger(7).toString() // "COURSE_TEES")
                            scores.add(newScore)
                        }
                        score.status = rows.rowCount()
                        promise.complete(scores)
                        conn.close()
                    }
                    .subscribe()
            }
            .subscribe()

        return promise.future()
    }

    override fun setScore(
        score: golf.handicap.Score
    ): Uni<Boolean> {
        val promise: io.vertx.mutiny.core.Promise<Boolean> = io.vertx.mutiny.core.Promise.promise()

        pool!!
            .rxGetConnection()
            .doOnSuccess { conn ->
                val parameters: Tuple = Tuple.tuple()

                getScoreByTeetime(score).onSuccess { scores ->
                    if (scores.isEmpty()) {
                        conn.rxBegin()
                            .doOnSuccess { tx ->
                                parameters.addString(score.golfer!!.pin)
                                parameters.addInteger(score.grossScore)
                                parameters.addFloat(score.netScore)
                                parameters.addInteger(score.adjustedScore)
                                parameters.addString(score.teeTime)
                                parameters.addFloat(score.handicap)
                                parameters.addInteger(score.course!!.course_key)
                                parameters.addInteger(score.course!!.teeId)

                                conn.preparedQuery(GETSCOREINSERT)
                                    .rxExecute(parameters)
                                    .doOnError { _ ->
                                        tx.rollback()
                                        conn.close()
                                    }
                                    .doOnSuccess { rows ->
                                        if (DbConfiguration.isUsingMariadb()) {
                                            score.scoreId = 0
                                            // rows.property(MySQLClient.LAST_INSERTED_ID).toInt();
                                        } else if (DbConfiguration.isUsingSqlite3()) {
                                            score.scoreId = rows.property(JDBCPool.GENERATED_KEYS).getInteger(0)
                                        }
                                        tx.commit()
                                        conn.close()
                                        updateGolfer(score).onSuccess { promise.complete(true) }
                                    }
                                    .subscribe(
                                        {},
                                        { err ->
                                            LOGGER.severe(
                                                String.format(
                                                    "%sError Adding Golfer Score - %s%s %s %s %s",
                                                    ColorUtilConstants.RED,
                                                    err,
                                                    ColorUtilConstants.RESET,
                                                    err.stackTraceToString(),
                                                    parameters.deepToString(),
                                                    GETSCOREINSERT
                                                )
                                            )
                                            promise.complete(false)
                                        }
                                    )
                            }
                            .subscribe(
                                {},
                                { err ->
                                    LOGGER.severe(
                                        String.format(
                                            "%sError Adding Golfer Score Subscribe - %s%s %s",
                                            ColorUtilConstants.RED,
                                            err,
                                            ColorUtilConstants.RESET,
                                            err.stackTraceToString()
                                        )
                                    )
                                }
                            )
                    } else {
                        conn.close()
                        updateScore(score).onSuccess {
                            updateGolfer(score).onSuccess { promise.complete(true) }
                        }
                    }
                }
            }
            .subscribe()
        return promise.future()
    }

    fun updateScore(score: golf.handicap.Score): Future<golf.handicap.Score> {
        val promise: Promise<golf.handicap.Score> = Promise.promise()

        pool!!
            .rxGetConnection()
            .doOnSuccess { conn ->
                conn.rxBegin()
                    .doOnSuccess { tx ->
                        val parameters: Tuple = Tuple.tuple()

                        parameters.addInteger(score.grossScore)
                        parameters.addInteger(score.adjustedScore)
                        parameters.addFloat(score.netScore)
                        parameters.addFloat(score.handicap)
                        parameters.addString(score.golfer!!.pin)
                        parameters.addString(score.teeTime)
                        parameters.addInteger(score.course!!.course_key)

                        conn.preparedQuery(GETSCOREUPDATE)
                            .rxExecute(parameters)
                            .doOnError { err ->
                                tx.rollback()
                                LOGGER.severe(
                                    String.format("Error getting score for Tee Time: %s", err.message)
                                )
                            }
                            .doOnSuccess { rows ->
                                score.status = rows.rowCount()
                                promise.complete(score)
                            }
                            .subscribe(
                                {
                                    tx.commit()
                                    conn.close()
                                },
                                { err ->
                                    LOGGER.severe(
                                        String.format(
                                            "%sError querying Course Score2 - %s%s %s",
                                            ColorUtilConstants.RED,
                                            err,
                                            ColorUtilConstants.RESET,
                                            err.stackTraceToString()
                                        )
                                    )
                                    promise.complete(score)
                                }
                            )
                    }
                    .subscribe()
            }
            .subscribe()
        return promise.future()
    }

    fun updateGolfer(score: golf.handicap.Score): Future<golf.handicap.Score> {
        val promise: Promise<golf.handicap.Score> = Promise.promise()

        pool!!
            .rxGetConnection()
            .doOnSuccess { conn ->
                conn.rxBegin()
                    .doOnSuccess { tx ->
                        val parameters: Tuple = Tuple.tuple()

                        parameters
                            .addInteger(if (score.golfer!!.overlap) 1 else 0)
                            .addInteger(if (score.golfer!!.public) 1 else 0)
                            .addString(score.golfer!!.pin)

                        conn.preparedQuery(GETGOLFERUPDATECHECKED)
                            .rxExecute(parameters)
                            .doOnSuccess { _ ->
                                tx.commit()
                                conn.close()
                                score.golfer!!.message = "Golfer public/overlap updated"
                                promise.complete(score)
                            }
                            .doOnError { err: Throwable ->
                                tx.rollback()
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
                                conn.close()
                            }
                            .subscribe(
                                {},
                                { err ->
                                    LOGGER.severe(
                                        String.format(
                                            "%sError Updating2 Golfer - %s%s",
                                            ColorUtilConstants.RED,
                                            err,
                                            ColorUtilConstants.RESET
                                        )
                                    )
                                    if (!promise.future().isComplete) {
                                        promise.complete(score)
                                    }
                                }
                            )
                    }
                    .subscribe()
            }
            .subscribe()

        return promise.future()
    }
}
