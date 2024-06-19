package golf.handicap.routes

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import dmo.fs.db.handicap.DbConfiguration
import dmo.fs.db.handicap.HandicapDatabase
import dmo.fs.quarkus.Server
import dmo.fs.utils.ColorUtilConstants
import golf.handicap.Golfer
import golf.handicap.Handicap
import golf.handicap.db.PopulateCourse
import golf.handicap.db.PopulateGolfer
import golf.handicap.db.PopulateGolferScores
import golf.handicap.db.PopulateScore
import golf.handicap.db.rx.IPopulateCourse
import golf.handicap.db.rx.IPopulateGolfer
import golf.handicap.db.rx.IPopulateGolferScores
import golf.handicap.db.rx.IPopulateScore
import handicap.grpc.*
import io.grpc.Status
import io.grpc.stub.StreamObserver
import io.quarkus.arc.properties.IfBuildProperty
import io.quarkus.arc.properties.UnlessBuildProperty
import io.quarkus.grpc.GrpcService
import io.vertx.core.Promise
import io.vertx.core.http.HttpMethod
import io.vertx.core.json.JsonObject
import io.vertx.mutiny.core.Context
import io.vertx.mutiny.core.Vertx
import io.vertx.mutiny.ext.web.Route
import io.vertx.mutiny.ext.web.Router
import io.vertx.mutiny.ext.web.handler.FaviconHandler
import io.vertx.mutiny.ext.web.handler.StaticHandler
import io.vertx.mutiny.ext.web.handler.TimeoutHandler
import io.vertx.mutiny.ext.web.handler.CorsHandler
import org.eclipse.microprofile.config.ConfigProvider
import java.util.*
import java.util.logging.Logger

class GrpcRoutes(vertx: Vertx, router: Router) : HandicapRoutes {
    val router: Router = router
    private val faviconHandler: FaviconHandler = FaviconHandler.create(vertx)
    var promise: Promise<Void> = Promise.promise()

    companion object {
        private val LOGGER = Logger.getLogger(GrpcRoutes::class.java.name)
        private var handicapDatabase: HandicapDatabase? = null
        private var isUsingHandicap: Boolean? = false
        var enableHandicapAdmin: Boolean? = null
        var handicapAdminPin: String? = null
        private var isUsingSqlite3: Boolean? = null
        private var isUsingH2: Boolean? = null
        private var isReactive: Boolean? = null

        init {
            var usingHandicap = System.getenv()["USE_HANDICAP"]
            if (usingHandicap != null) {
                isUsingHandicap = "true" == usingHandicap
            } else {
                usingHandicap = System.getProperty("USE_HANDICAP")
                isUsingHandicap = "true" == usingHandicap
            }
        }
    }

    init {
        getConfig(vertx)
        isUsingSqlite3 = DbConfiguration.isUsingSqlite3()
        isReactive = isUsingSqlite3!!
    }

    private fun getConfig(vertx: Vertx) {
        val context = Optional.ofNullable<Context>(vertx.orCreateContext)
        if (context.isPresent) {
            val jsonObject = Optional.ofNullable<JsonObject>(vertx.orCreateContext.config())
            try {
                var appConfig = if (jsonObject.isPresent) jsonObject.get() else JsonObject()
                if (appConfig.isEmpty) {
                    val jsonMapper = ObjectMapper()
                    var node: JsonNode?
                    javaClass.getResourceAsStream("/application-conf.json")
                        .use { `in` -> node = jsonMapper.readTree(`in`) }
                    appConfig = JsonObject.mapFrom(node)
                }
                if (isUsingHandicap == null) {
                    val config = ConfigProvider.getConfig()
                    val enableHandicap =
                        Optional.ofNullable(config.getValue("handicap.enableHandicap", Boolean::class.java))
                    if (enableHandicap.isPresent) isUsingHandicap = enableHandicap.get()
                }
                val enableAdmin = Optional.ofNullable(appConfig.getBoolean("handicap.enableAdmin"))
                if (enableAdmin.isPresent) enableHandicapAdmin = enableAdmin.get()
                val handicapPin = Optional.ofNullable(appConfig.getString("handicap.adminPin"))
                if (handicapPin.isPresent) handicapAdminPin = handicapPin.get()

                if (isUsingHandicap!!) {
                    handicapDatabase = DbConfiguration.getDefaultDb()
                    if (handicapDatabase == null) {
                        val warning = String.format(
                            """%s
                            When using 'DEFAULT_DB=h2', no database setup is required for dev.
                            However, it is best to use postgres or mariadb.  %s""",
                            ColorUtilConstants.GREEN,
                            ColorUtilConstants.RESET,
                        )
                        throw Exception(
                            """
                            When using Handicap, DEFAULT_DB must be 'h2', 'mariadb' or 'postgres'.
                            $warning
                            """
                        )
                    }
                }
            } catch (exception: java.lang.Exception) {
                exception.printStackTrace()
            }
        }
    }

    override fun getVertxRouter(handicapPromise: Promise<Void>): Router {
        val staticHandler: StaticHandler = StaticHandler.create("")
        staticHandler.setCachingEnabled(false)
        staticHandler.setMaxAgeSeconds(0)
        if (handicapDatabase != null && isUsingHandicap!!) {
            handicapDatabase?.checkOnTables()?.onItem()!!.invoke { ->
                val staticRoute: Route = router.route("/handicap/*").handler(TimeoutHandler.create(2000))
                val corsHandler = CorsHandler.create()
                corsHandler.addOrigin("Access-Control-Allow-Origin: *")
                corsHandler.addOrigin("Access-Control-Allow-Headers: *")
                val methods =
                    setOf(HttpMethod.GET, HttpMethod.POST, HttpMethod.PUT, HttpMethod.OPTIONS, HttpMethod.HEAD)
                corsHandler.allowedMethods(methods)
                staticRoute.handler(corsHandler)
                staticRoute.handler(staticHandler)
                staticRoute.failureHandler {
                        err,
                    ->
                    LOGGER.severe(String.format("FAILURE in static route: %s", err.statusCode()))
                }
                router.route().handler(staticHandler)
                router.route().handler(faviconHandler)
                handicapPromise.complete()
            }.onFailure().invoke { err ->
                err.stackTrace
            }
                .subscribeAsCompletionStage()
        } else {
            handicapPromise.complete()
        }
        if (isUsingHandicap!! && handicapDatabase.toString().contains("H2")) {
            handicapPromise.tryComplete()
        }

        return router
    }

    override fun setRoutePromise(promise: Promise<Void>) {
        this.promise = promise
    }

    override fun routes(router: Router): Router {
        router.get("/handicap/courses").produces("application/json").handler {
            it.response().send("{}")
        }

        return router
    }

    /*
        To start up Handicap without environment variable, comment line below
     */
    @IfBuildProperty(name = "USE_HANDICAP", stringValue = "true")
    /*
        To run without setting - DEFAULT_DB=h2 or postgres or mariadb
        1. set "dodex.default.db" entry in .../src/main/resources/application.properties file
        2. uncomment line below
   */
//    @IfBuildProperty(name = "dodex.default.db", stringValue = "h2", enableIfMissing = true)
    @UnlessBuildProperty(name = "DEFAULT_DB", stringValue = "sqlite3", enableIfMissing = true)
    @UnlessBuildProperty(name = "DEFAULT_DB", stringValue = "ibmdb2", enableIfMissing = true)
    @UnlessBuildProperty(name = "DEFAULT_DB", stringValue = "firebase", enableIfMissing = true)
    @UnlessBuildProperty(name = "DEFAULT_DB", stringValue = "neo4j", enableIfMissing = true)
    @UnlessBuildProperty(name = "DEFAULT_DB", stringValue = "cassandra", enableIfMissing = true)
    @UnlessBuildProperty(name = "DEFAULT_DB", stringValue = "cubrid", enableIfMissing = true)
    @IfBuildProperty(name = "handicap.enableHandicap", stringValue = "true")
    @GrpcService
    class HandicapIndexService : HandicapIndexGrpc.HandicapIndexImplBase() {
        init {
            Server.setIsUsingHandicap(true)
        }

        override fun listCourses(
            request: Command,
            responseObserver: StreamObserver<ListCoursesResponse?>
        ) {
            val populateCourse: IPopulateCourse = if (isReactive!!) {
                golf.handicap.db.rx.PopulateCourse()
            } else {
                PopulateCourse()
            }
            val course: golf.handicap.Course = golf.handicap.Course()
            course.courseState = request.key

            populateCourse.getCourses(course).onItem().invoke { coursesBuilder ->
                responseObserver.onNext(coursesBuilder.build())
                responseObserver.onCompleted()
            }.subscribeAsCompletionStage()
        }

        override fun addRating(request: Command, responseObserver: StreamObserver<HandicapData?>) {
            val populateCourse: IPopulateCourse = if (isReactive!!) {
                golf.handicap.db.rx.PopulateCourse()
            } else {
                PopulateCourse()
            }
            val mapper = ObjectMapper()

            val ratingMap =
                mapper.readValue(request.json, object : TypeReference<HashMap<String, Any>>() {})

            val color: String = ratingMap["color"] as String
            if (!color.startsWith("#")) {
                val rgb: List<String> = color.split("(")[1].split(")")[0].split(",")
                val hex = "%02x"

                ratingMap["color"] = String.format(
                    "#%s%s%s",
                    hex.format(rgb[0].trim().toInt()),
                    hex.format(rgb[1].trim().toInt()),
                    hex.format(rgb[2].trim().toInt())
                )
                    .uppercase()
            }

            populateCourse
                .getCourseWithTee(ratingMap)
                .onItem().invoke { handicapData ->
                    responseObserver.onNext(handicapData)
                    responseObserver.onCompleted()
                }
                .onFailure().invoke { err ->
                    LOGGER.severe("Error Adding Rating: " + err.message)
                    responseObserver.onCompleted()
                }.subscribeAsCompletionStage()
        }

        override fun addScore(request: Command, responseObserver: StreamObserver<HandicapData?>) {
            val mapper = ObjectMapper()
            val score = mapper.readValue(request.json, object : TypeReference<golf.handicap.Score>() {})

            val populateScore: IPopulateScore = if (isReactive!!) {
                golf.handicap.db.rx.PopulateScore()
            } else {
                PopulateScore()
            }
            populateScore
                .setScore(score)
                .onItem().invoke { ->
                    val handicap = Handicap()

                    handicap
                        .getHandicap(score.golfer!!)
                        .onItem().invoke { latestTee ->
                            val newHandicap: Float = latestTee["handicap"] as Float
                            val slope: Float = latestTee["slope"] as Float
                            val rating: Float = latestTee["rating"] as Float
                            val par: Int = latestTee["par"] as Int
                            score.handicap = newHandicap
                            val courseHandicap: Float = newHandicap * slope / 113 + (rating - par)
                            score.netScore = score.grossScore.toFloat() - courseHandicap
                            score.golfer!!.handicap = newHandicap
                            populateScore
                                .setScore(score)
                                .onItem().invoke { ->
                                    responseObserver.onNext(
                                        HandicapData.newBuilder()
                                            .setMessage("Success")
                                            .setCmd(request.cmd)
                                            .setJson(ObjectMapper().writeValueAsString(score))
                                            .build()
                                    )
                                    responseObserver.onCompleted()
                                }
                                .onFailure().invoke { err ->
                                    err.stackTrace
                                    responseObserver.onCompleted()
                                }.subscribeAsCompletionStage()
                        }
                        .onFailure().invoke { err ->
                            err.stackTrace
                            responseObserver.onCompleted()
                        }.subscribeAsCompletionStage()
                }
                .onFailure().invoke { err ->
                    err.stackTrace
                    responseObserver.onCompleted()
                }.subscribeAsCompletionStage()
        }

        override fun getGolfer(
            request: HandicapSetup,
            responseObserver: StreamObserver<HandicapData?>
        ) {
            if ("Test" == request.message) {
                LOGGER.warning("Got json from Client: " + request.json)
            }

            var requestJson = JsonObject(request.json)
            val golfer = requestJson.mapTo(Golfer::class.java)
            val cmd = request.cmd

            if (cmd < 0 || cmd > 8) {
                val status: Status = Status.FAILED_PRECONDITION.withDescription("Cmd - Not between 0 and 8")
                responseObserver.onError(status.asRuntimeException())
            } else {
                val populateGolfer: IPopulateGolfer = if (isReactive!!) {
                    golf.handicap.db.rx.PopulateGolfer()
                } else {
                    PopulateGolfer()
                }

                populateGolfer.getGolfer(golfer, cmd).onItem().invoke { resultGolfer ->
                    requestJson = JsonObject.mapFrom(resultGolfer)
                    requestJson.remove("status")
                    requestJson.put("status", resultGolfer?.status)
                    if (enableHandicapAdmin!!) {
                        requestJson.put("adminstatus", 10)
                        requestJson.put("admin", handicapAdminPin)
                    }
                    responseObserver.onNext(
                        HandicapData.newBuilder()
                            .setMessage(resultGolfer?.message)
                            .setCmd(request.cmd)
                            .setJson(requestJson.toString())
                            .build()
                    )
                    if ("Test" == request.message) {
                        LOGGER.warning("Handicap Data Sent: " + request.json)
                    }
                    responseObserver.onCompleted()
                }.subscribeAsCompletionStage()
            }
        }

        override fun golferScores(request: Command, responseObserver: StreamObserver<HandicapData?>) {
            val populateScores: IPopulateGolferScores = if (isReactive!!) {
                golf.handicap.db.rx.PopulateGolferScores()
            } else {
                PopulateGolferScores()
            }
            val requestJson = JsonObject(request.json)
            val golfer = requestJson.mapTo(Golfer::class.java)
            if (request.cmd == 10) {
                val names = request.key.split("&#44;")
                golfer.lastName = names[0]
                golfer.firstName = if (names.size > 1) names[1].trim() else ""
                golfer.pin = ""
            }

            populateScores.getGolferScores(golfer, 365)!!.onItem().invoke { scoresMap ->
                responseObserver.onNext(
                    HandicapData.newBuilder()
                        .setMessage("Success")
                        .setCmd(request.cmd)
                        .setJson(scoresMap["array"].toString())
                        .build()
                )
                responseObserver.onCompleted()
            }.subscribeAsCompletionStage()
        }

        override fun listGolfers(
            request: Command,
            responseObserver: StreamObserver<ListPublicGolfers?>
        ) {
            val populateGolfer: IPopulateGolfer = if (isReactive!!) {
                golf.handicap.db.rx.PopulateGolfer()
            } else {
                PopulateGolfer()
            }

            populateGolfer.getGolfers().onItem().invoke { listGolfersBuilder ->
                responseObserver.onNext(listGolfersBuilder.build())
                responseObserver.onCompleted()
            }.subscribeAsCompletionStage()
        }

        override fun removeScore(request: Command, responseObserver: StreamObserver<HandicapData?>) {
            val populateScores: IPopulateGolferScores = if (isReactive!!) {
                golf.handicap.db.rx.PopulateGolferScores()
            } else {
                PopulateGolferScores()
            }
            val requestJson = JsonObject(request.json)
            val golfer = requestJson.mapTo(Golfer::class.java)
            if (golfer.pin != null) {
                populateScores.removeLastScore(request.key).onItem().invoke { used ->
                    val handicap = Handicap()
                    handicap.getHandicap(golfer).onItem().invoke { latestTee ->
                        golfer.handicap = latestTee["handicap"] as Float
                        val jsonObject = JsonObject.mapFrom(golfer)
                        jsonObject.put("used", used)

                        responseObserver.onNext(
                            HandicapData.newBuilder()
                                .setMessage("Success")
                                .setCmd(request.cmd)
                                .setJson(jsonObject.toString())
                                .build()
                        )
                        responseObserver.onCompleted()
                    }.subscribeAsCompletionStage()
                }.subscribeAsCompletionStage()
            } else {
                responseObserver.onCompleted()
            }
        }
    }
}
