package golf.handicap.db.rx

import dmo.fs.utils.ColorUtilConstants
import golf.handicap.Golfer
import io.vertx.core.Future
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.reactivex.core.Promise
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.logging.Logger

class Handicap {
    companion object {
        private val LOGGER = Logger.getLogger(Handicap::class.java.name)
    }

    var dateTime: Date? = null
    private var diffkeys = Array(20) { " " }
    var index = intArrayOf(0, 0, 0, 0, 1, 1, 2, 2, 3, 3, 4, 4, 5, 5, 6, 6, 7, 8, 9, 10)
    private var diffScores = FloatArray(20) { -100f }

    private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    var multiple = .96.toFloat()
    var used: MutableMap<String, Used> = HashMap<String, Used>()
    var teeTime: String? = null
    var key: String = String()
    private var usedClass: Used? = null

    fun getHandicap(golfer: Golfer): Future<MutableMap<String, Any>> {
        val handicapPromise = Promise.promise<MutableMap<String, Any>>()
        val averageSlope = 113
        val golferScores = PopulateGolferScores()

        var numberOfScores = -1
        var adjusted: Float
        var rating: Float
        var slope: Float
        var difference: Float
        var handicapDifferential: Float
        var handicap: BigDecimal
        val latestTee: MutableMap<String, Any> = HashMap()

        val handicapData: Future<Map<String, Any?>>? = golferScores.getGolferScores(golfer)

        handicapData!!.onSuccess { map ->
            if (map.isEmpty()) {
                latestTee["handicap"] = 0.0f
                handicapPromise.complete(latestTee)
            } else {
                val dataArray: JsonArray = map["array"] as JsonArray
                val objects = dataArray.iterator()

                var i = 0
                do {
                    val jsonObject: JsonObject = objects.next() as JsonObject

                    if (jsonObject.getInteger("ADJUSTED_SCORE") != null) {
                        adjusted = jsonObject.getInteger("ADJUSTED_SCORE").toFloat()
                        rating = jsonObject.getDouble("TEE_RATING").toFloat()
                        slope = jsonObject.getInteger("TEE_SLOPE").toFloat()
                        difference = adjusted - rating
                        handicapDifferential = averageSlope * difference / slope
                        // Used to calculate Net Score
                        if (i == 0) {
                            latestTee["adjusted"] = adjusted
                            latestTee["rating"] = rating
                            latestTee["slope"] = slope
                            latestTee["par"] = jsonObject.getInteger("TEE_PAR")
                        }

                        var foundZero = false
                        for (x in 0..19) {
                            if (diffScores[x] == -100.0f) {
                                diffScores[x] = handicapDifferential
                                foundZero = true
                                break
                            }
                        }

                        if (!foundZero && handicapDifferential < diffScores[19]) {
                            diffScores[19] = handicapDifferential
                        }

                        Arrays.sort(diffScores)

                        val localDateTime = LocalDateTime.parse(jsonObject.getString("TEE_TIME"))
                        key = handicapDifferential.toString() + '\t' + formatter.format(
                            localDateTime
                        )
                        key = if (key.indexOf('.') == 1) "0$key" else key
                        diffkeys[i] = key
                        used[key] = Used(
                            jsonObject.getString("PIN"),
                            jsonObject.getInteger("COURSE_SEQ"),
                            jsonObject.getString("TEE_TIME")
                        )

                        if (numberOfScores < 19) numberOfScores++
                        i++
                    }
                } while (objects.hasNext())

                Arrays.sort(diffkeys)
                handicap = calculateHandicap(numberOfScores)
                golfer.handicap = handicap.toDouble()
                golferScores.setGolferHandicap(golfer).onSuccess { _ ->
                    var rowsUpdated = 0
                    usedClass = used[diffkeys[19]]

                    golferScores.clearUsed(usedClass!!.pin).onSuccess { count ->
                        rowsUpdated += count

                        var idx = 19
                        while (idx > -1 && diffkeys[idx] != "") {
                            usedClass = used[diffkeys[idx]]
                            if (usedClass != null && usedClass!!.used) {
                                golferScores.setUsed(usedClass!!.pin, usedClass!!.course!!, usedClass!!.teeTime)
                                    .onSuccess { usedCount ->
                                        rowsUpdated += usedCount
                                    }
                                    .onFailure { err ->
                                        LOGGER.severe(
                                            String.format(
                                                "%sError Setting Used - %s%s",
                                                ColorUtilConstants.RED,
                                                err.message,
                                                ColorUtilConstants.RESET
                                            )
                                        )
                                    }
                            }
                            idx--
                        }
                    }
                        .onFailure { err ->
                            LOGGER.severe(
                                String.format(
                                    "%sError Clearing Used - %s%s",
                                    ColorUtilConstants.RED,
                                    err.message,
                                    ColorUtilConstants.RESET
                                )
                            )
                        }
                }
                latestTee["handicap"] = handicap.toFloat()
                handicapPromise.complete(latestTee)
            }
        }

        return handicapPromise.future()
    }

    private fun calculateHandicap(numberOfScores: Int): BigDecimal {
        if (diffScores[15] == -100.0f) return BigDecimal(0.0)
        var total = 0.0.toFloat()
        var i = 0
        val j = index[numberOfScores]

        while (i < j) {
            total += diffScores[19 - numberOfScores + i]
            setUsed(diffkeys[19 - numberOfScores + i])
            i++
        }
        total = total / index[numberOfScores] * multiple
        return BigDecimal(total.toString()).setScale(1, RoundingMode.DOWN)
    }

    private fun setUsed(diffkey: String) {
        usedClass = used[diffkey]
        if (usedClass != null) {
            usedClass!!.used = true
            used[diffkey] = usedClass as Used
        }
    }

    inner class Used(pin: String?, course: Int?, teeTime: String) {
        var pin: String?
        var course: Int?
        var teeTime: String
        var used = false
        override fun toString(): String {
            return StringBuffer().append(pin).append(" ").append(course).append(" ")
                .append(teeTime).append(" ").append(used)
                .toString()
        }

        init {
            this.pin = pin
            this.course = course
            this.teeTime = teeTime
        }
    }
}
