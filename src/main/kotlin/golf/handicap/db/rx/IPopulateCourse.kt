package golf.handicap.db.rx

import golf.handicap.Course
import handicap.grpc.HandicapData
import handicap.grpc.ListCoursesResponse
import io.smallrye.mutiny.Uni
import java.sql.SQLException

interface IPopulateCourse {

    fun getCourses(
        course: Course
    ): Uni<ListCoursesResponse.Builder>

    @Throws(SQLException::class, InterruptedException::class)
    fun setCourse(
        courseMap: HashMap<String, Any>
    ): Uni<Boolean?>

    @Throws(SQLException::class, InterruptedException::class)
    fun setRating(
        courseMap: HashMap<String, Any>
    ): Uni<Boolean>

    @Throws(SQLException::class, InterruptedException::class)
    fun updateTee(
        courseMap: HashMap<String, Any>
    ): Uni<Boolean>

    @Throws(SQLException::class, InterruptedException::class)
    fun getCourseWithTee(
        courseMap: java.util.HashMap<String, Any>
    ): Uni<HandicapData?>
}