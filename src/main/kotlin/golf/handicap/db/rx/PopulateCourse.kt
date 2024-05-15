package golf.handicap.db.rx

import dmo.fs.db.wsnext.DbConfiguration
import dmo.fs.utils.ColorUtilConstants
import golf.handicap.Course
import golf.handicap.generated.tables.references.COURSE
import golf.handicap.generated.tables.references.RATINGS
import handicap.grpc.*
import io.smallrye.mutiny.Uni
import io.vertx.core.Future
import io.vertx.core.json.JsonObject
import io.vertx.reactivex.core.Promise
import io.vertx.rxjava3.jdbcclient.JDBCPool
import io.vertx.rxjava3.sqlclient.Tuple
import org.jooq.*
import org.jooq.impl.*
import org.jooq.impl.DSL.*
import java.sql.*
import java.util.*
import java.util.logging.Logger

class PopulateCourse : SqlConstants() , IPopulateCourse {
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
            return """update ratings 
						set TEE_COLOR = ?,
						TEE_RATING = ?,
						TEE_SLOPE = ?,
						TEE_PAR = ?
						where COURSE_SEQ = ?
							and TEE = ?"""
        }
    }

    private fun getCourse(courseMap: HashMap<String, Any>): Future<Course> {
        val promise: Promise<Course> = Promise.promise()
        val course = Course()

        pool!!
            .rxGetConnection()
            .doOnSuccess { conn ->
                val sql = GETCOURSEBYNAME
                val parameters: Tuple = Tuple.tuple()

                parameters.addString(courseMap["courseName"] as String)
                parameters.addString(courseMap["country"] as String)
                parameters.addString(courseMap["state"] as String)
                course.findNextRating()

                conn.preparedQuery(sql)
                    .rxExecute(parameters)
                    .doOnError { err ->
                        LOGGER.severe(String.format("Error getting course: %s", err.message))
                    }
                    .doOnSuccess { rows ->
                        for (row in rows) {
                            course.courseKey = row.getInteger(0) // "COURSE_SEQ")
                            course.courseName = row.getString(1) // "COURSE_NAME")
                            course.courseCountry = row.getString(2) // "COURSE_COUNTRY")
                            course.courseState = row.getString(3) // "COURSE_STATE")
                        }
                        promise.complete(course)
                    }
                    .subscribe(
                        { conn.close() },
                        { err ->
                            LOGGER.severe(
                                String.format(
                                    "%sError querying Course - %s%s %s\n%s",
                                    ColorUtilConstants.RED,
                                    err,
                                    ColorUtilConstants.RESET,
                                    err.stackTraceToString()
                                )
                            )
                            promise.complete(course)
                        }
                    )
            }
            .subscribe()

        return promise.future()
    }

    override fun getCourseWithTee(
        courseMap: HashMap<String, Any>
    ): Uni<HandicapData?> {
        val promise: io.vertx.mutiny.core.Promise<HandicapData?> = io.vertx.mutiny.core.Promise.promise()
        val course = Course()
        pool!!
            .rxGetConnection()
            .doOnSuccess { conn ->
                val sql = GETCOURSEBYTEE
                val parameters: Tuple = Tuple.tuple()
                var updateTees = true

                parameters.addString(courseMap["courseName"] as String)
                parameters.addString(courseMap["country"] as String)
                parameters.addString(courseMap["state"] as String)
                parameters.addInteger(courseMap["tee"] as Int)
                conn.preparedQuery(sql)
                    .rxExecute(parameters)
                    .doOnError { err ->
                        LOGGER.warning(String.format("Error getting course: %s", err.message))
                    }
                    .doOnSuccess { rows ->
                        for (row in rows) {
                            course.courseKey = row.getInteger(0) // "COURSE_SEQ")
                            course.courseName = row.getString(1) // "COURSE_NAME")
                            course.courseCountry = row.getString(2) // "COURSE_COUNTRY")
                            course.courseState = row.getString(3) // "COURSE_STATE")
                            course.setRating(
                                row.getInteger(0), // "COURSE_SEQ"),
                                row.getFloat(7).toString(), // "TEE_RATING"),
                                row.getInteger(8), // "TEE_SLOPE"),
                                row.getInteger(9), // "TEE_PAR"),
                                row.getInteger(5), // "TEE"),
                                row.getString(6) // "TEE_COLOR")
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
                            setCourse(courseMap).onItem().invoke {_ ->
                                setRating(courseMap).onItem().invoke {_ -> course.resetIterator() }
                                    .subscribeAsCompletionStage()
                            }.subscribeAsCompletionStage()
                        } else {
                            if (updateTees) {
                                updateTee(courseMap).onItem().invoke{_ -> }
                                    .onFailure { err ->
                                    LOGGER.severe(
                                        String.format(
                                            "%sError updating tees - %s%s %s",
                                            ColorUtilConstants.RED,
                                            err,
                                            ColorUtilConstants.RESET,
                                            err.stackTraceToString()
                                        )
                                    )
                                    false
                                }
                            }
                        }
                    }
                    .subscribe(
                        {
                            conn.delegate.close()
                            val jsonString: String = JsonObject(courseMap).toString()
                                val handicapData = HandicapData.newBuilder()
                                    .setMessage("Success")
                                    .setCmd(2)
                                    .setJson(jsonString)
                                    .build()
                            promise.complete(handicapData)
                        },
                        { err ->
                            LOGGER.severe(
                                String.format(
                                    "%sError querying Courses Tees - %s%s %s",
                                    ColorUtilConstants.RED,
                                    err,
                                    ColorUtilConstants.RESET,
                                    err.stackTraceToString()
                                )
                            )
                            promise.complete()
                        }
                    )
            }
            .subscribe()

        return promise.future()
    }

    @Throws(SQLException::class, InterruptedException::class)
    override fun getCourses(
        course: Course
    ): Uni<ListCoursesResponse.Builder> {
        val promise: io.vertx.mutiny.core.Promise<ListCoursesResponse.Builder> = io.vertx.mutiny.core.Promise.promise()

        pool!!
            .rxGetConnection()
            .doOnSuccess { conn ->
                val parameters: Tuple = Tuple.tuple()
                parameters.addString(course.courseState)
                val sql: String? = GETCOURSESBYSTATE

                val coursesBuilder = ListCoursesResponse.newBuilder()
                var courseBuilder: handicap.grpc.Course.Builder? = null

                conn.preparedQuery(sql)
                    .rxExecute(parameters)
                    .doOnError { err ->
                        LOGGER.severe(String.format("Error getting courses: %s", err.message))
                    }
                    .doOnSuccess { rows ->
                        val ratingTees: Array<Int> = arrayOf(-1, -1, -1, -1, -1)
                        for (row in rows) {
                            if (courseBuilder == null || row!!.getInteger(0) != courseBuilder!!.id) {
                                if (courseBuilder != null) {
                                    setUndefinedTees(ratingTees, courseBuilder!!)
                                    coursesBuilder.addCourses(courseBuilder)
                                }

                                courseBuilder = handicap.grpc.Course.newBuilder()
                                courseBuilder!!.id = row.getInteger(0) // "COURSE_SEQ"))
                                courseBuilder!!.name = row.getString(1) // "COURSE_NAME"))
                            }

                            val ratingBuilder =
                                Rating.newBuilder()
                                    .setRating(row.getFloat(7).toString()) // "TEE_RATING")
                                    .setSlope(row.getInteger(8)) // "TEE_SLOPE")
                                    .setTee(row.getInteger(5)) // "TEE")
                                    .setColor(row.getString(6)) // "TEE_COLOR")
                                    .setPar(row.getInteger(9)) // "TEE_PAR")
                            courseBuilder!!.addRatings(ratingBuilder)
                            ratingTees[row.getInteger(5)] = row.getInteger(5) // which tees have been added
                        }

                        if (courseBuilder != null) {
                            setUndefinedTees(ratingTees, courseBuilder!!)
                            coursesBuilder.addCourses(courseBuilder)
                        }

                        conn.rxClose().doOnSubscribe {
                            promise.complete(coursesBuilder)
                        }.subscribe()
                    }
                    .subscribe(
                        {},
                        { err ->
                            LOGGER.severe(
                                String.format(
                                    "%sError querying Courses - %s%s\n%s",
                                    ColorUtilConstants.RED,
                                    err,
                                    ColorUtilConstants.RESET,
                                    err.stackTraceToString()
                                )
                            )
                            conn.rxClose().subscribe()
                            promise.complete(coursesBuilder)
                        }
                    )
            }
            .subscribe()
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
        val ratingPromise: io.vertx.mutiny.core.Promise<Boolean> = io.vertx.mutiny.core.Promise.promise()

        getCourse(courseMap).onSuccess { queriedCourse ->
            if (queriedCourse.courseKey == 0) {
                pool!!
                    .connection
                    .flatMap { conn ->
                        conn.begin().doOnSuccess { tx ->
                            val parameters: Tuple = Tuple.tuple()
                            parameters.addString(courseMap["courseName"] as String)
                            parameters.addString(courseMap["country"] as String)
                            parameters.addString(courseMap["state"] as String)
                            conn.preparedQuery(GETCOURSEINSERT)
                                .execute(parameters)
                                .doOnSuccess { rows ->
                                    for (row in rows) {
                                        courseMap["courseKey"] = row.getInteger(0)
                                    }
                                    if (DbConfiguration.isUsingSqlite3()) {
                                        courseMap["courseKey"] = rows.property(JDBCPool.GENERATED_KEYS).getInteger(0)
                                    }

                                    tx.commit()
                                    conn.close()
                                    ratingPromise.complete(true)
                                }
                                .doOnError { _ ->
                                    tx.rollback()
                                    ratingPromise.complete(false)
                                }
                                .subscribe(
                                    {},
                                    { err ->
                                        LOGGER.severe(
                                            String.format(
                                                "%sError Inserting Course - %s%s %s",
                                                ColorUtilConstants.RED,
                                                err.message,
                                                ColorUtilConstants.RESET,
                                                err.stackTraceToString()
                                            )
                                        )
                                    }
                                )
                        }
                    }
                    .doOnError { err ->
                        LOGGER.severe(
                            String.format(
                                "%sError Inserting Course - %s%s -- %s",
                                ColorUtilConstants.RED,
                                err.message,
                                ColorUtilConstants.RESET,
                                err.stackTraceToString()
                            )
                        )
                    }
                    .subscribe()
            } else {
                ratingPromise.complete(false)
            }
        }
        return ratingPromise.future()
    }

    @Throws(SQLException::class, InterruptedException::class)
    override fun setRating(
        courseMap: HashMap<String, Any>
    ): Uni<Boolean> {
        val ratingPromise: io.vertx.mutiny.core.Promise<Boolean> = io.vertx.mutiny.core.Promise.promise()

        getCourse(courseMap).onSuccess { queryedCourse ->
            if (queryedCourse.courseKey > 0) {
                courseMap["courseKey"] = queryedCourse.courseKey
            }

            if (queryedCourse.courseKey > 0 || courseMap["courseKey"] as Int > 0) {
                pool!!
                    .rxGetConnection()
                    .flatMap { conn ->
                        conn.rxBegin().doOnSuccess { tx ->
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

                            conn.preparedQuery(GETRATINGINSERT)
                                .rxExecute(parameters)
                                .doOnSuccess { _ ->
                                    tx.commit()
                                    ratingPromise.complete(true)
                                }
                                .doOnError { _ ->
                                    tx.rollback()
                                    ratingPromise.complete(false)
                                }
                                .subscribe(
                                    { conn.close() },
                                    { err ->
                                        LOGGER.severe(
                                            String.format(
                                                "%sError Inserting Rating - %s%s %s",
                                                ColorUtilConstants.RED,
                                                err.message,
                                                ColorUtilConstants.RESET,
                                                err.stackTraceToString()
                                            )
                                        )
                                        conn.close()
                                    }
                                )
                        }
                    }
                    .doOnError { err ->
                        LOGGER.severe(
                            String.format(
                                "%sError Inserting Rating - %s%s -- %s",
                                ColorUtilConstants.RED,
                                err,
                                ColorUtilConstants.RESET,
                                err.stackTraceToString()
                            )
                        )
                        ratingPromise.complete(false)
                    }
                    .subscribe()
            }
        }

        return ratingPromise.future()
    }

    @Throws(SQLException::class, InterruptedException::class)
    override fun updateTee(
        courseMap: HashMap<String, Any>
    ): Uni<Boolean> {
        val ratingPromise: io.vertx.mutiny.core.Promise<Boolean> = io.vertx.mutiny.core.Promise.promise()

        pool!!
            .rxGetConnection()
            .flatMap { conn ->
                conn.rxBegin().doOnSuccess { tx ->
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
                    conn.preparedQuery(sql)
                        .rxExecute(parameters)
                        .doOnSuccess { _ ->
                            tx.commit()
                            ratingPromise.complete(true)
                        }
                        .doOnError { _ ->
                            tx.rollback()
                            ratingPromise.complete(false)
                        }
                        .subscribe(
                            { conn.close() },
                            { err ->
                                LOGGER.severe(
                                    String.format(
                                        "%sError Update Tee Rating - %s%s %s",
                                        ColorUtilConstants.RED,
                                        err.message,
                                        ColorUtilConstants.RESET,
                                        err.stackTraceToString()
                                    )
                                )
                            }
                        )
                }
            }
            .doOnError { err ->
                LOGGER.severe(
                    String.format(
                        "%sError Update Rating - %s%s -- %s",
                        ColorUtilConstants.RED,
                        err.message,
                        ColorUtilConstants.RESET,
                        err.stackTraceToString()
                    )
                )
                ratingPromise.complete(false)
            }
            .subscribe()

        return ratingPromise.future()
    }
}
