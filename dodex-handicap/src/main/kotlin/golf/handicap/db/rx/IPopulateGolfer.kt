package golf.handicap.db.rx

import golf.handicap.Golfer
import handicap.grpc.ListPublicGolfers
import io.smallrye.mutiny.Uni
import java.sql.SQLException

interface IPopulateGolfer {
    @Throws(SQLException::class, InterruptedException::class)
    fun getGolfer(golfer: Golfer, cmd: Int): Uni<Golfer?>

//    @Throws(SQLException::class, InterruptedException::class)
//    fun getGolfers(
//        responseObserver: StreamObserver<ListPublicGolfers?>
//    ): Uni<StreamObserver<ListPublicGolfers?>>

    @Throws(SQLException::class, InterruptedException::class)
    fun addGolfer(golfer: Golfer): Uni<Golfer>
    fun updateGolfer(golfer: Golfer, golferClone: Golfer?, cmd: Int): Uni<Golfer>

    @Throws(SQLException::class, InterruptedException::class)
    fun getGolfers(
    ): Uni<ListPublicGolfers.Builder>
}