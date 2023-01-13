package golf.handicap.db.rx

import golf.handicap.Golfer
import io.smallrye.mutiny.Uni

interface IPopulateGolferScores {
    @Throws(Exception::class)
    fun getGolferScores(golfer: Golfer, rows: Int): Uni<Map<String, Any?>>?
    @Throws(Exception::class)
    fun removeLastScore(golferPIN: String?): Uni<String>
}