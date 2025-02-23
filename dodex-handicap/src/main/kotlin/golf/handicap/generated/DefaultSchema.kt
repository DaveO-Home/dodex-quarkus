/*
 * This file is generated by jOOQ.
 */
package golf.handicap.generated


import golf.handicap.generated.sequences.COURSE_ID_SEQ
import golf.handicap.generated.tables.Course
import golf.handicap.generated.tables.Golfer
import golf.handicap.generated.tables.Groups
import golf.handicap.generated.tables.Member
import golf.handicap.generated.tables.Ratings
import golf.handicap.generated.tables.SchemaMigrations
import golf.handicap.generated.tables.Scores

import kotlin.collections.List

import org.jooq.Catalog
import org.jooq.Sequence
import org.jooq.Table
import org.jooq.impl.SchemaImpl


/**
 * This class is generated by jOOQ.
 */
@Suppress("UNCHECKED_CAST")
open class DefaultSchema : SchemaImpl("", DefaultCatalog.DEFAULT_CATALOG) {
    companion object {

        /**
         * The reference instance of <code>DEFAULT_SCHEMA</code>
         */
        val DEFAULT_SCHEMA: DefaultSchema = DefaultSchema()
    }

    /**
     * The table <code>course</code>.
     */
    val COURSE: Course get() = Course.COURSE

    /**
     * The table <code>golfer</code>.
     */
    val GOLFER: Golfer get() = Golfer.GOLFER

    /**
     * The table <code>groups</code>.
     */
    val GROUPS: Groups get() = Groups.GROUPS

    /**
     * The table <code>member</code>.
     */
    val MEMBER: Member get() = Member.MEMBER

    /**
     * The table <code>ratings</code>.
     */
    val RATINGS: Ratings get() = Ratings.RATINGS

    /**
     * The table <code>schema_migrations</code>.
     */
    val SCHEMA_MIGRATIONS: SchemaMigrations get() = SchemaMigrations.SCHEMA_MIGRATIONS

    /**
     * The table <code>scores</code>.
     */
    val SCORES: Scores get() = Scores.SCORES

    override fun getCatalog(): Catalog = DefaultCatalog.DEFAULT_CATALOG

    override fun getSequences(): List<Sequence<*>> = listOf(
        COURSE_ID_SEQ
    )

    override fun getTables(): List<Table<*>> = listOf(
        Course.COURSE,
        Golfer.GOLFER,
        Groups.GROUPS,
        Member.MEMBER,
        Ratings.RATINGS,
        SchemaMigrations.SCHEMA_MIGRATIONS,
        Scores.SCORES
    )
}
