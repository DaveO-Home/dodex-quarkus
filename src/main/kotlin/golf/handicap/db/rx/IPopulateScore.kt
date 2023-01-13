package golf.handicap.db.rx

import golf.handicap.Score
import io.smallrye.mutiny.Uni

interface IPopulateScore {
    fun setScore(score: Score): Uni<Boolean>
}