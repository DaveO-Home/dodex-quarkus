@file:JvmName("PopulateGolfer")

package golf.handicap.db

import dmo.fs.db.handicap.DbConfiguration
import dmo.fs.utils.ColorUtilConstants
import golf.handicap.*
import golf.handicap.Golfer
import golf.handicap.db.rx.IPopulateGolfer
import golf.handicap.generated.tables.references.GOLFER
import handicap.grpc.*
import io.smallrye.mutiny.Uni
import io.vertx.mutiny.core.Promise
import io.vertx.mutiny.sqlclient.Tuple
import org.jooq.impl.DSL.*
import java.sql.SQLException
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.logging.Logger


class PopulateGolfer : SqlConstants(), IPopulateGolfer {
    companion object {
        private val LOGGER = Logger.getLogger(PopulateGolfer::class.java.name)

        @Throws(SQLException::class)
        @JvmStatic
        fun buildSql() {

            val regEx = "\\$\\d".toRegex()
            GETGOLFER = if (qmark) setupGetGolfer().replace(regEx, "?") else setupGetGolfer()
            GETGOLFERBYNAME =
                if (qmark) setupGetGolferByName().replace(regEx, "?") else setupGetGolferByName()
            GETGOLFERBYNAMES =
                if (qmark) setupGetGolferByNames().replace(regEx, "?") else setupGetGolferByNames()
            INSERTGOLFER = if (qmark) setupInsertGolfer().replace(regEx, "?") else setupInsertGolfer()
            UPDATEGOLFER = if (qmark) setupUpdateGolfer().replace(regEx, "?") else setupUpdateGolfer()
            UPDATEGOLFERNAME =
                if (qmark) setupUpdateGolferName().replace(regEx, "?") else setupUpdateGolferName()
            UPDATEGOLFERHANDICAP =
                if (qmark) setupUpdateGolferHandicap().replace(regEx, "?")
                else setupUpdateGolferHandicap()
            DELETEGOLFER = if (qmark) setupDeleteGolfer().replace(regEx, "?") else setupDeleteGolfer()
            GETPUBLICGOLFERS =
                if (qmark) setupGetPublicGolfers().replace(regEx, "?")
                else setupGetPublicGolfers().replace("\"", "")
        }

        @JvmStatic
        fun setupGetGolfer(): String {
            return create!!.renderNamedParams(
                select(
                    field("PIN"),
                    field("FIRST_NAME"),
                    field("LAST_NAME"),
                    field("HANDICAP"),
                    field("COUNTRY"),
                    field("STATE"),
                    field("OVERLAP_YEARS"),
                    field("PUBLIC"),
                    field("LAST_LOGIN")
                )
                    .from(table("GOLFER"))
                    .where(field("PIN").eq("$"))
            )
        }

        @JvmStatic
        fun setupGetGolferByName(): String {
            return create!!.renderNamedParams(
                select(
                    field("PIN"),
                    field("FIRST_NAME"),
                    field("LAST_NAME"),
                    field("HANDICAP"),
                    field("COUNTRY"),
                    field("STATE"),
                    field("OVERLAP_YEARS"),
                    field("PUBLIC"),
                    field("LAST_LOGIN")
                )
                    .from(table("GOLFER"))
                    .where(field("LAST_NAME").eq("$"))
            )
        }

        @JvmStatic
        fun setupGetGolferByNames(): String {
            return create!!.renderNamedParams(
                select(
                    field("PIN"),
                    field("FIRST_NAME"),
                    field("LAST_NAME"),
                    field("HANDICAP"),
                    field("COUNTRY"),
                    field("STATE"),
                    field("OVERLAP_YEARS"),
                    field("PUBLIC"),
                    field("LAST_LOGIN")
                )
                    .from(table("GOLFER"))
                    .where(field("LAST_NAME").eq("$"))
                    .and(field("FIRST_NAME").eq("$"))
            )
        }

        @JvmStatic
        fun setupGetPublicGolfers(): String {
            return create!!.renderNamedParams(
                select(GOLFER.LAST_NAME, GOLFER.FIRST_NAME)
                    .from(GOLFER)
                    .where(GOLFER.PUBLIC.isTrue)
                    .orderBy(GOLFER.LAST_NAME)
            )
        }

        @JvmStatic
        fun setupInsertGolfer(): String {
            return create!!.renderNamedParams(
                insertInto(table("GOLFER"))
                    .columns(
                        field("FIRST_NAME"),
                        field("LAST_NAME"),
                        field("PIN"),
                        field("COUNTRY"),
                        field("STATE"),
                        field("OVERLAP_YEARS"),
                        field("PUBLIC"),
                        field("LAST_LOGIN")
                    )
                    .values("$", "$", "$", "S", "$", "$", "$", "$")
                    .returning(field("PIN"))
            )
        }

        @JvmStatic
        fun setupUpdateGolferName(): String {
            return create!!.renderNamedParams(
                update(table("GOLFER"))
                    .set(field("FIRST_NAME"), "$")
                    .set(field("LAST_NAME"), "$")
                    .where(field("pin").eq("$"))
            )
        }

        @JvmStatic
        fun setupUpdateGolfer(): String {
            return create!!.renderNamedParams(
                update(table("GOLFER"))
                    .set(field("COUNTRY"), "$")
                    .set(field("STATE"), "$")
                    .set(field("OVERLAP_YEARS"), "$")
                    .set(field("PUBLIC"), "$")
                    .set(field("LAST_LOGIN"), "$")
                    .where(field("pin").eq("$"))
            )
        }

        @JvmStatic
        fun setupUpdateGolferHandicap(): String {
            return create!!.renderNamedParams(
                update(table("GOLFER")).set(field("HANDICAP"), "$").where(field("pin").eq("$"))
            )
        }

        @JvmStatic
        fun setupDeleteGolfer(): String {
            return create!!.renderNamedParams(deleteFrom(table("GOLFER")).where(field("pin").eq("$")))
        }
    }

    @Throws(SQLException::class, InterruptedException::class)
    override fun getGolfer(golfer: Golfer, cmd: Int): Uni<Golfer?> {
        val promise: Promise<Golfer> = Promise.promise()

        pool!!
            .connection { conn ->
                val parameters: Tuple = Tuple.tuple()
                var sql: String? = GETGOLFER
                val golferPin: String? = golfer.pin
                if (golferPin!!.trim { it <= ' ' } != "") {
                    parameters.addString(golfer.pin)
                    /*
                        If pin is missing try using first/last name with last course/tee/tee-date
                    */
                } else if (golfer.lastName != null && golfer.firstName != null) {
                    sql = GETGOLFERBYNAMES
                    parameters.addString(golfer.lastName)
                    parameters.addString(golfer.firstName)
                }
                conn.preparedQuery(sql)
                    .execute(parameters)
                    .onItem().invoke { rows ->
                        golfer.message = "Golfer not found"
                        var golferClone: Golfer? = null
                        for (row in rows) {
                            golferClone = golfer.clone() as Golfer
                            golfer.pin = row!!.getString(0) // PIN
                            golfer.firstName = row.getString(1) // FIRST_NAME
                            golfer.lastName = row.getString(2) // LAST_NAME
                            golfer.handicap = row.getFloat(3) // HANDICAP
                            golfer.country = row.getString(4) // COUNTRY
                            golfer.state = row.getString(5) // STATE")
                            golfer.overlap = row.getBoolean(6) // .equals(1) // OVERLAP_YEARS
                            golfer.public = row.getBoolean(7) //.equals(1) // PUBLIC

                            if (DbConfiguration.isUsingMariadb()) {
                                val zdt: ZonedDateTime =
                                    ZonedDateTime.of(row.getLocalDateTime(8), ZoneId.systemDefault())
                                val zdl: Long = zdt.toInstant().toEpochMilli()
                                golfer.lastLogin = zdl
                            } else {
                                golfer.lastLogin = row.getLong(8) // "LAST_LOGIN")
                            }
                            golfer.message = "Golfer not found"
                        }

                        if (rows.size() == 0) {
                            if (golfer.firstName!!.length < 3 || golfer.lastName!!.length < 5) {
                                golfer.status = -1
                                var which = "Last"
                                if (golfer.firstName!!.length < 3) {
                                    which = "First"
                                }
                                golfer.message = "$which name required for new golfer."
                                promise.complete(golfer)
                            } else {
                                val future = addGolfer(golfer)
                                future
                                    .onFailure().invoke { throwable ->
                                        golfer.message = throwable.message
                                        golfer.status = -2
                                        promise.complete(golfer)
                                    }
                                    .onItem().invoke { resultGolfer -> promise.complete(resultGolfer) }
                                    .subscribeAsCompletionStage()
                            }
                        } else {
                            val futureUpdate: Uni<Golfer> = updateGolfer(golfer, golferClone, cmd)
                            futureUpdate
                                .onFailure().invoke { throwable ->
                                    golfer.message = throwable.message
                                    golfer.status = -3
                                    promise.complete(golferClone)
                                }
                                .onItem().invoke { updateGolfer -> promise.complete(updateGolfer) }
                                .subscribeAsCompletionStage()
                        }
                    }
                    .onFailure().invoke { err ->
                        golfer.status = -1
                        golfer.message = "Golfer query failed"
                        LOGGER.severe(
                            String.format(
                                "%sError querying Golfer - %s%s %s",
                                ColorUtilConstants.RED,
                                err,
                                ColorUtilConstants.RESET,
                                err.stackTraceToString()
                            )
                        )
                    }.onTermination().invoke { ->
                        conn.close().subscribeAsCompletionStage()
                        promise.tryComplete(golfer)
                    }
                    .subscribeAsCompletionStage()
                Uni.createFrom().item(golfer)
            }
            .subscribeAsCompletionStage()

        return promise.future()
    }

    @Throws(SQLException::class, InterruptedException::class)
    override fun getGolfers(
    ): Uni<ListPublicGolfers.Builder> {
        val promise: Promise<ListPublicGolfers.Builder> = Promise.promise()

        pool!!
            .connection
            .onItem().invoke { conn ->
                val golfersBuilder = ListPublicGolfers.newBuilder()

                var sql = GETPUBLICGOLFERS
                // MariaDb uses tinyint for boolean
                if(DbConfiguration.isUsingH2() || DbConfiguration.isUsingPostgres()) {
                    sql = sql!!.replace("= 1", "= true")
                }
                if(DbConfiguration.isUsingMariadb()) {
                    sql = sql!!.uppercase()
                }

                conn.query(sql)
                    .execute()
                    .onItem().invoke { rows ->
                        var golferBuilder: handicap.grpc.Golfer.Builder?

                        for (row in rows) {
                            val concatName = row!!.getString(0) + ", " + row.getString(1)
                            golferBuilder = handicap.grpc.Golfer.newBuilder()

                            golferBuilder.name = concatName
                            golfersBuilder.addGolfer(golferBuilder)
                        }
                    }
                    .onFailure().invoke { err ->
                        LOGGER.severe(
                            String.format(
                                "%sError Querying Golfers Public - %s%s %s",
                                ColorUtilConstants.RED,
                                err,
                                ColorUtilConstants.RESET,
                                err.stackTraceToString()
                            )
                        )
                    }.onTermination().invoke { ->
                        promise.complete(golfersBuilder)
                        conn.close().subscribeAsCompletionStage()
                    }
                    .subscribeAsCompletionStage()
            }
            .subscribeAsCompletionStage()

        return promise.future()
    }

    @Throws(SQLException::class, InterruptedException::class)
    override fun addGolfer(golfer: Golfer): Uni<Golfer> {
        val promise: Promise<Golfer> = Promise.promise()
        val localDateTime: LocalDateTime = LocalDateTime.now()

        if (golfer.pin == null || golfer.pin!!.length < 6) {
            golfer.status = -1
            golfer.message = "Valid Golfer Pin must be supplied"
            promise.complete(golfer)
            return promise.future()
        }

        pool!!
            .connection
            .onItem().invoke { conn ->
                val parameters: Tuple = Tuple.tuple()
                val ldt = LocalDateTime.now()
                val milliSeconds =
                    ZonedDateTime.of(ldt, ZoneId.systemDefault()).toInstant().toEpochMilli()

                parameters
                    .addString(golfer.firstName)
                    .addString(golfer.lastName)
                    .addString(golfer.pin)
                    .addString(golfer.country)
                    .addString(golfer.state)

                parameters.addBoolean(golfer.overlap).addBoolean(golfer.public)

                if (DbConfiguration.isUsingMariadb()) parameters.addValue(ldt)
                else parameters.addValue(milliSeconds)

                val sql: String? = INSERTGOLFER
                val zdt: ZonedDateTime = ZonedDateTime.of(localDateTime, ZoneId.systemDefault())
                golfer.lastLogin = zdt.toInstant().toEpochMilli()

                conn.preparedQuery(sql)
                    .execute(parameters)
                    .onItem().invoke { ->
                        golfer.message = "Golfer added"
                    }
                    .onFailure().invoke { err ->
                        golfer.status = -1
                        golfer.message = "Golfer add failed"
                        LOGGER.severe(
                            String.format(
                                "%sError Adding Golfer - %s%s %s",
                                ColorUtilConstants.RED,
                                err,
                                ColorUtilConstants.RESET,
                                err.stackTraceToString()
                            )
                        )
                    }.onTermination().invoke { ->
                        promise.tryComplete(golfer)
                        conn.close().subscribeAsCompletionStage()
                    }
                    .subscribeAsCompletionStage()
            }
            .subscribeAsCompletionStage()

        return promise.future()
    }

    override fun updateGolfer(golfer: Golfer, golferClone: Golfer?, cmd: Int): Uni<Golfer> {
        val promise: Promise<Golfer> = Promise.promise()
        val isLogin = cmd == 3 || golferClone?.pin?.length == 0

        pool!!
            .connection
            .onItem().invoke { conn ->
                val parameters: Tuple = Tuple.tuple()
                val ldt = LocalDateTime.now()
                val milliSeconds =
                    ZonedDateTime.of(ldt, ZoneId.systemDefault()).toInstant().toEpochMilli()
                val overlap = if (isLogin) golfer.overlap else golferClone?.overlap
                val public = if (isLogin) golfer.public else golferClone?.public

                parameters
                    .addString(if (isLogin) golfer.country else golferClone?.country)
                    .addString(if (isLogin) golfer.state else golferClone?.state)
                    .addBoolean(overlap!!)
                    .addBoolean(public!!)
                if (DbConfiguration.isUsingMariadb()) {
                    parameters.addValue(ldt)
                } else {
                    parameters.addLong(milliSeconds)
                }
                parameters.addString(golfer.pin)

                val sql: String? = UPDATEGOLFER

                conn.preparedQuery(sql)
                    .execute(parameters)
                    .onItem().invoke { _ ->
                        conn.close()
                        if (!isLogin) {
                            golfer.message = "Golfer updated"
                            golfer.country = golferClone?.country
                            golfer.state = golferClone?.state
                            golfer.overlap = golferClone?.overlap == true
                            golfer.public = golferClone?.public == true
                        }
                        golfer.lastLogin = milliSeconds
                    }
                    .onFailure().invoke { err ->
                        golfer.status = -1
                        golfer.message = "Golfer update failed"
                        LOGGER.severe(
                            String.format(
                                "%sError Updating Golfer - %s%s %s",
                                ColorUtilConstants.RED,
                                err,
                                ColorUtilConstants.RESET,
                                err.stackTraceToString()
                            )
                        )
                    }.onTermination().invoke { ->
                        promise.tryComplete(golfer)
                        conn.close().subscribeAsCompletionStage()

                    }
                    .subscribeAsCompletionStage()
            }
            .subscribeAsCompletionStage()

        return promise.future()
    }
}
