package golf.handicap

import java.sql.Connection


class Courses {
    private var selectedCourses: MutableList<Course>? = null
    private var coursesByState: MutableMap<String, Course>? = null
    var iterator: Iterator<*>? = null
    var currentCourse: Course? = null
    fun init() {
        selectedCourses!!.clear()
        coursesByState!!.clear()
    }

    fun getCourses(connection: Connection?, course_state: String?): List<Course>? {
        if(connection == null) {
            println("$connection : $course_state")
        }
        return null
    }
}