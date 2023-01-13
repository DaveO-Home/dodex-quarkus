package golf.handicap.db

import dmo.fs.db.handicap.DbConfiguration
import dmo.fs.utils.ColorUtilConstants
import golf.handicap.Course
import golf.handicap.db.rx.IPopulateCourse
import golf.handicap.generated.tables.references.COURSE
import golf.handicap.generated.tables.references.RATINGS
import handicap.grpc.*
import io.smallrye.mutiny.Uni
import io.vertx.core.json.JsonObject
import io.vertx.mutiny.core.Promise
import io.vertx.mutiny.mysqlclient.MySQLClient
import io.vertx.mutiny.sqlclient.PropertyKind
import io.vertx.mutiny.sqlclient.Tuple
import org.jooq.*
import org.jooq.impl.*
import org.jooq.impl.DSL.*
import java.sql.*
import java.util.*
import java.util.logging.Logger

class PopulateCourse : SqlConstants(), IPopulateCourse {
    companion object {
        private val LOGGER = Logger.getLogger(PopulateCourse::class.java.name)
        private val regEx = "\\$\\d".toRegex()

        @Throws(SQLException::class)
        @JvmStatic
        fun buildSql() {
            GETCOURSESBYSTATE =
                if (qmark) setupCoursesByState().replace(regEx, "?")
                else setupCoursesByState().replace("\"", "")

            GETCOURSEBYNAME =
                if (qmark) setupCourseByName().replace(regEx, "?")
                else setupCourseByName().replace("\"", "")

            GETCOURSEBYTEE =
                if (qmark) setupCourseByRating().replace(regEx, "?")
                else setupCourseByRating().replace("\"", "")

            GETCOURSEINSERT =
                if (qmark) setupCourseInsert().replace(regEx, "?")
                else setupCourseInsert().replace("\"", "")

            GETRATINGINSERT =
                if (qmark) setupRatingInsert().replace(regEx, "?")
                else setupRatingInsert().replace("\"", "")

            GETRATINGUPDATE =
                if (qmark) setupRatingUpdate().replace(regEx, "?")
                else setupRatingUpdate().replace("\"", "")

            GETSQLITERATINGUPDATE =
                if (qmark) setupSqliteRatingUpdate().replace(regEx, "?")
                else setupSqliteRatingUpdate().replace("\"", "")
        }

//        init {}

        @JvmStatic
        private fun setupCoursesByState(): String {
            return create!!.renderNamedParams(
                select(
                    COURSE.COURSE_SEQ,
                    COURSE.COURSE_NAME,
                    COURSE.COURSE_COUNTRY,
                    COURSE.COURSE_STATE,
                    RATINGS.COURSE_SEQ,
                    RATINGS.TEE,
                    RATINGS.TEE_COLOR,
                    RATINGS.TEE_RATING,
                    RATINGS.TEE_SLOPE,
                    RATINGS.TEE_PAR
                )
                    .from(COURSE, RATINGS)
                    .where(COURSE.COURSE_STATE.eq("$").and(COURSE.COURSE_SEQ.eq(RATINGS.COURSE_SEQ)))
            )
        }

        @JvmStatic
        private fun setupCourseByName(): String {
            return create!!.renderNamedParams(
                select(COURSE.COURSE_SEQ, COURSE.COURSE_NAME, COURSE.COURSE_COUNTRY, COURSE.COURSE_STATE)
                    .from(COURSE)
                    .where(COURSE.COURSE_NAME.eq("$"))
                    .and(COURSE.COURSE_COUNTRY.eq("$"))
                    .and(COURSE.COURSE_STATE.eq("$"))
            )
        }

        @JvmStatic
        private fun setupCourseByRating(): String {

            return create!!.renderNamedParams(
                select(
                    COURSE.COURSE_SEQ,
                    COURSE.COURSE_NAME,
                    COURSE.COURSE_COUNTRY,
                    COURSE.COURSE_STATE,
                    RATINGS.COURSE_SEQ,
                    RATINGS.TEE,
                    RATINGS.TEE_COLOR,
                    RATINGS.TEE_RATING,
                    RATINGS.TEE_SLOPE,
                    RATINGS.TEE_PAR
                )
                    .from(COURSE, RATINGS)
                    .where(
                        COURSE
                            .COURSE_NAME
                            .eq("$")
                            .and(COURSE.COURSE_COUNTRY.eq("$"))
                            .and(COURSE.COURSE_STATE.eq("$"))
                            .and(COURSE.COURSE_SEQ.eq(RATINGS.COURSE_SEQ))
                            .and(RATINGS.TEE.eq(0))
                    )
            )
        }

        @JvmStatic
        private fun setupCourseInsert(): String {
            return create!!.renderNamedParams(
                insertInto(COURSE, COURSE.COURSE_NAME, COURSE.COURSE_COUNTRY, COURSE.COURSE_STATE)
                    .values("$", "$", "$").returning(field("COURSE_SEQ"))
            )
        }

        @JvmStatic
        private fun setupRatingInsert(): String {
            return create!!.renderNamedParams(
                insertInto(
                    RATINGS,
                    RATINGS.COURSE_SEQ,
                    RATINGS.TEE,
                    RATINGS.TEE_COLOR,
                    RATINGS.TEE_RATING,
                    RATINGS.TEE_SLOPE,
                    RATINGS.TEE_PAR
                )
                    .values(0, 0, "$", 0.0f, 0, 0)
            )
        }

        @JvmStatic
        private fun setupCourseUpdate(): String {
            return create!!.renderNamedParams(
                insertInto(COURSE, COURSE.COURSE_NAME, COURSE.COURSE_STATE).values("$", "$")
            )
        }

        @JvmStatic
        private fun setupRatingUpdate(): String {
            return create!!.renderNamedParams(
                update(RATINGS)
                    .set(RATINGS.TEE_COLOR, "$")
                    .set(RATINGS.TEE_RATING, 0.0f)
                    .set(RATINGS.TEE_SLOPE, 0)
                    .set(RATINGS.TEE_PAR, 0)
                    .where(RATINGS.COURSE_SEQ.eq(0).and(RATINGS.TEE.eq(0)))
            )
        }

        @JvmStatic
        private fun setupSqliteRatingUpdate(): String {
            return """update RATINGS 
						set TEE_COLOR = ?,
						TEE_RATING = ?,
						TEE_SLOPE = ?,
						TEE_PAR = ?
						where COURSE_SEQ = ?
							and TEE = ?"""
        }
    }

    private fun getCourse(courseMap: HashMap<String, Any>): Uni<Course> {
        val promise: Promise<Course> = Promise.promise()
        val course = Course()

        pool!!.connection.onItem().invoke { conn ->
            val sql = GETCOURSEBYNAME
            val parameters: Tuple = Tuple.tuple()

            parameters.addString(courseMap["courseName"] as String)
            parameters.addString(courseMap["country"] as String)
            parameters.addString(courseMap["state"] as String)
            course.findNextRating()
            conn.preparedQuery(sql).execute(parameters).onFailure().invoke { err ->
                LOGGER.severe(
                    String.format(
                        "%sError querying Course - %s%s %s\n%s",
                        ColorUtilConstants.RED,
                        err,
                        ColorUtilConstants.RESET,
                        err.stackTraceToString()
                    )
                )
            }
                .onItem().invoke { rows ->
                    for (row in rows) {
                        course.courseKey = row.getInteger(0) // COURSE_SEQ
                        course.courseName = row.getString(1) // COURSE_NAME
                        course.courseCountry = row.getString(2) // COURSE_COUNTRY
                        course.courseState = row.getString(3) // COURSE_STATE
                    }
                }
                .onTermination().invoke { ->
                    conn.close().subscribeAsCompletionStage()
                    promise.complete(course)
                }.subscribeAsCompletionStage()
        }.subscribeAsCompletionStage()

        return promise.future()
    }

    override fun getCourseWithTee(
        courseMap: HashMap<String, Any>
    ): Uni<HandicapData?> {
        val promise: Promise<HandicapData?> = Promise.promise()
        val course = Course()

        pool!!.connection.onItem().invoke { conn ->
            val sql = GETCOURSEBYTEE
            val parameters: Tuple = Tuple.tuple()
            var updateTees = true
            var didError = false

            parameters.addString(courseMap["courseName"] as String)
            parameters.addString(courseMap["country"] as String)
            parameters.addString(courseMap["state"] as String)
            parameters.addInteger(courseMap["tee"] as Int)

            conn.preparedQuery(sql).execute(parameters).onFailure().invoke { err ->
                LOGGER.severe(
                    String.format(
                        "%sError querying course by tee - %s%s %s\n%s",
                        ColorUtilConstants.RED,
                        err,
                        ColorUtilConstants.RESET,
                        err.stackTraceToString()
                    )
                )
                didError = true
            }
                .onItem().invoke { rows ->
                    for (row in rows) {
                        course.courseKey = row.getInteger(0) // COURSE_SEQ
                        course.courseName = row.getString(1) // COURSE_NAME
                        course.courseCountry = row.getString(2) // COURSE_COUNTRY
                        course.courseState = row.getString(3) // COURSE_STATE
                        course.setRating(
                            row.getInteger(0), // COURSE_SEQ
                            row.getFloat(7).toString(), // TEE_RATING
                            row.getInteger(8), // TEE_SLOPE
                            row.getInteger(9), // TEE_PAR
                            row.getInteger(5), // TEE
                            row.getString(6) // TEE_COLOR
                        )

                        if ((courseMap["rating"] as String) == row.getFloat(7).toString() &&
                            (courseMap["slope"] as Int) == row.getInteger(8) &&
                            (courseMap["par"] as Int) == row.getInteger(9) &&
                            (courseMap["color"] as String) == row.getString(6)
                        ) {
                            updateTees = false
                        }
                    }
                    if (rows.size() == 0) {
                        updateTees = false
                        courseMap["status"] = 2
                        setCourse(courseMap).onItem().invoke { ->
                            setRating(courseMap).onItem().invoke { ->
                                course.resetIterator()
                            }.onFailure().invoke { err ->
                                LOGGER.severe(
                                    String.format(
                                        "%sError setting tees - %s%s %s",
                                        ColorUtilConstants.RED,
                                        err,
                                        ColorUtilConstants.RESET,
                                        err.stackTraceToString()
                                    )
                                )
                            }
                        }.subscribeAsCompletionStage()
                    } else {
                        if (updateTees) {
                            updateTee(courseMap).onFailure().invoke { err ->
                                LOGGER.severe(
                                    String.format(
                                        "%sError updating tees - %s%s %s",
                                        ColorUtilConstants.RED,
                                        err,
                                        ColorUtilConstants.RESET,
                                        err.stackTraceToString()
                                    )
                                )
                                promise.complete()
                            }.subscribeAsCompletionStage()
                        }
                    }
                }
                .onTermination().invoke { ->
                    conn.close().subscribeAsCompletionStage()
                    var message = "Success"
                    courseMap["status"] = 0
                    if (didError) {
                        message = "Failure"
                        courseMap["status"] = -1
                    }

                    val jsonString: String = JsonObject(courseMap).toString()
                    val handicapData = HandicapData.newBuilder()
                        .setMessage(message)
                        .setCmd(2)
                        .setJson(jsonString)
                        .build()
                    promise.complete(handicapData)
                }.subscribeAsCompletionStage()
        }.subscribeAsCompletionStage()

        return promise.future()
    }

    @Throws(SQLException::class, InterruptedException::class)
    override fun getCourses(
        course: Course
    ): Uni<ListCoursesResponse.Builder> {
        val promise: Promise<ListCoursesResponse.Builder> = Promise.promise()

        pool!!.connection.onItem().invoke { conn ->
            val parameters: Tuple = Tuple.tuple()
            parameters.addString(course.courseState)
            val sql: String? = GETCOURSESBYSTATE

            val coursesBuilder = ListCoursesResponse.newBuilder()
            var courseBuilder: handicap.grpc.Course.Builder? = null

            conn.preparedQuery(sql).execute(parameters).onFailure().invoke { err ->
                LOGGER.severe(
                    String.format(
                        "%sError querying courses - %s%s %s\n%s",
                        ColorUtilConstants.RED,
                        err,
                        ColorUtilConstants.RESET,
                        err.stackTraceToString()
                    )
                )
            }
                .onItem().invoke { rows ->
                    val ratingTees: Array<Int> = arrayOf(-1, -1, -1, -1, -1)
                    for (row in rows) {
                        if (courseBuilder == null || row!!.getInteger(0) != courseBuilder!!.id) {
                            if (courseBuilder != null) {
                                setUndefinedTees(ratingTees, courseBuilder!!)
                                coursesBuilder.addCourses(courseBuilder)
                            }

                            courseBuilder = handicap.grpc.Course.newBuilder()
                            courseBuilder!!.id = row.getInteger(0) // COURSE_SEQ
                            courseBuilder!!.name = row.getString(1) // COURSE_NAME
                        }

                        val ratingBuilder =
                            Rating.newBuilder()
                                .setRating(row.getFloat(7).toString()) // TEE_RATING
                                .setSlope(row.getInteger(8)) // TEE_SLOPE
                                .setTee(row.getInteger(5)) // TEE
                                .setColor(row.getString(6)) // TEE_COLOR
                                .setPar(row.getInteger(9)) // TEE_PAR
                        courseBuilder!!.addRatings(ratingBuilder)
                        ratingTees[row.getInteger(5)] = row.getInteger(5) // which tees have been added
                    }

                    if (courseBuilder != null) {
                        setUndefinedTees(ratingTees, courseBuilder!!)
                        coursesBuilder.addCourses(courseBuilder)
                    }
                }
                .onTermination().invoke { ->
                    conn.close().subscribeAsCompletionStage()
                    promise.complete(coursesBuilder)
                }
                .subscribeAsCompletionStage()
        }.subscribeAsCompletionStage()

        return promise.future()
    }

    private fun setUndefinedTees(
        ratingTees: Array<Int>,
        courseBuilder: handicap.grpc.Course.Builder
    ) {
        for (int in ratingTees.indices) {
            if (ratingTees[int] == -1) {
                val ratingBuilder = Rating.newBuilder().setTee(int)
                courseBuilder.addRatings(ratingBuilder)
            }
            ratingTees[int] = -1
        }
    }

    @Throws(SQLException::class, InterruptedException::class)
    override fun setCourse(
        courseMap: HashMap<String, Any>
    ): Uni<Boolean?> {
        val ratingPromise: Promise<Boolean> = Promise.promise()

        getCourse(courseMap).onItem().invoke { queriedCourse ->
            var isInserted = false
            if (queriedCourse.courseKey == 0) {
                pool!!.withTransaction { conn ->
                    val courseKey = "courseKey"
                    val parameters: Tuple = Tuple.tuple()
                    parameters.addString(courseMap["courseName"] as String)
                    parameters.addString(courseMap["country"] as String)
                    parameters.addString(courseMap["state"] as String)

                    val cf = conn.preparedQuery(GETCOURSEINSERT)
                        .execute(parameters).onItem().invoke { rows ->
                            for (row in rows) {
                                courseMap[courseKey] = row.getInteger(0)
                            }
                            val generatedKey = PropertyKind.create(
                                "generated-keys",
                                io.vertx.sqlclient.Row::class.java
                            )

                            if (DbConfiguration.isUsingSqlite3() && courseMap[courseKey] == null) {
                                courseMap["courseKey"] = rows.property(generatedKey).getInteger(0)
                            } else if (DbConfiguration.isUsingMariadb() && courseMap[courseKey] == null) {
                                courseMap["courseKey"] = rows.property(MySQLClient.LAST_INSERTED_ID)
                            }

                            isInserted = true
                        }
                        .onFailure().invoke { err ->
                            LOGGER.severe(
                                String.format(
                                    "%sError Inserting Course - %s%s %s",
                                    ColorUtilConstants.RED,
                                    err.message,
                                    ColorUtilConstants.RESET,
                                    err.stackTraceToString()
                                )
                            )
                            isInserted = false
                        }.onTermination().invoke { ->
                            ratingPromise.complete(isInserted)
                            conn.close()
                        }
                        .subscribeAsCompletionStage()

                    Uni.createFrom().item(cf)
                }.onFailure().invoke { err ->
                    LOGGER.severe(
                        String.format(
                            "%sError With JDBC Pool - %s%s %s",
                            ColorUtilConstants.RED,
                            err.message,
                            ColorUtilConstants.RESET,
                            err.stackTraceToString()
                        )
                    )
                    isInserted = false
                }.subscribeAsCompletionStage()
            } else {
                ratingPromise.complete(isInserted)
            }
        }.subscribeAsCompletionStage()

        return ratingPromise.future()
    }

    @Throws(SQLException::class, InterruptedException::class)
    override fun setRating(
        courseMap: HashMap<String, Any>
    ): Uni<Boolean> {
        val ratingPromise: Promise<Boolean> = Promise.promise()

        getCourse(courseMap).onItem().invoke { queriedCourse ->
            var isInserted = true
            if (queriedCourse.courseKey > 0) {
                courseMap["courseKey"] = queriedCourse.courseKey
            }

            if (queriedCourse.courseKey > 0 || courseMap["courseKey"] as Int > 0) {
                pool!!.withTransaction { conn ->
                    val parameters: Tuple = Tuple.tuple()
                    parameters.addInteger(courseMap["courseKey"] as Int)
                    parameters.addInteger(courseMap["tee"] as Int)
                    parameters.addString(courseMap["color"] as String)
                    if (DbConfiguration.isUsingPostgres()) {
                        parameters.addFloat((courseMap["rating"] as String).toFloat())
                    } else {
                        parameters.addString(courseMap["rating"] as String)
                    }
                    parameters.addInteger(courseMap["slope"] as Int)
                    parameters.addInteger(courseMap["par"] as Int)
                    val cf = conn.preparedQuery(GETRATINGINSERT)
                        .execute(parameters)
                        .onFailure().invoke { err ->
                            LOGGER.severe(
                                String.format(
                                    "%sError Inserting Rating - %s%s %s",
                                    ColorUtilConstants.RED,
                                    err.message,
                                    ColorUtilConstants.RESET,
                                    err.stackTraceToString()
                                )
                            )
                            isInserted = false
                        }.onTermination().invoke { ->
                            ratingPromise.complete(isInserted)
                            conn.close().subscribeAsCompletionStage()
                        }.subscribeAsCompletionStage()
                    Uni.createFrom().item(cf)
                }.subscribeAsCompletionStage()
            }
        }.subscribeAsCompletionStage()

        return ratingPromise.future()
    }

    @Throws(SQLException::class, InterruptedException::class)
    override fun updateTee(
        courseMap: HashMap<String, Any>
    ): Uni<Boolean> {
        val ratingPromise: Promise<Boolean> = Promise.promise()
        var isUpdated = true
        pool!!.withTransaction { conn ->
            val parameters: Tuple = Tuple.tuple()
            parameters.addString(courseMap["color"] as String)
            if (DbConfiguration.isUsingPostgres()) {
                parameters.addFloat((courseMap["rating"] as String).toFloat())
            } else {
                parameters.addString(courseMap["rating"] as String)
            }
            parameters.addInteger(courseMap["slope"] as Int)
            parameters.addInteger(courseMap["par"] as Int)
            parameters.addInteger(courseMap["seq"] as Int)
            parameters.addInteger(courseMap["tee"] as Int)

            val sql: String? =
                if (DbConfiguration.isUsingSqlite3()) GETSQLITERATINGUPDATE else GETRATINGUPDATE
            val cf = conn.preparedQuery(sql)
                .execute(parameters)
                .onFailure().invoke { err ->
                    LOGGER.severe(
                        String.format(
                            "%sError Update Tee Rating - %s%s %s",
                            ColorUtilConstants.RED,
                            err.message,
                            ColorUtilConstants.RESET,
                            err.stackTraceToString()
                        )
                    )
                    isUpdated = false
                }.onTermination().invoke { ->
                    ratingPromise.complete(isUpdated)
                    conn.close()
                }.subscribeAsCompletionStage()
            Uni.createFrom().item(cf)
        }.subscribeAsCompletionStage()

        return ratingPromise.future()
    }
}
