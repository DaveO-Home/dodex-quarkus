/*
 * This file is generated by jOOQ.
 */
package golf.handicap.generated.tables


import golf.handicap.generated.DefaultSchema
import golf.handicap.generated.keys.GOLFER_NAMES_UNIQUE
import golf.handicap.generated.keys.GOLFER_PKEY
import golf.handicap.generated.keys.SCORES__FK_GOLFER_SCORES
import golf.handicap.generated.tables.Scores.ScoresPath
import golf.handicap.generated.tables.records.GolferRecord

import kotlin.collections.Collection
import kotlin.collections.List

import org.jooq.Condition
import org.jooq.Field
import org.jooq.ForeignKey
import org.jooq.InverseForeignKey
import org.jooq.Name
import org.jooq.Path
import org.jooq.PlainSQL
import org.jooq.QueryPart
import org.jooq.Record
import org.jooq.SQL
import org.jooq.Schema
import org.jooq.Select
import org.jooq.Stringly
import org.jooq.Table
import org.jooq.TableField
import org.jooq.TableOptions
import org.jooq.UniqueKey
import org.jooq.impl.DSL
import org.jooq.impl.Internal
import org.jooq.impl.SQLDataType
import org.jooq.impl.TableImpl


/**
 * This class is generated by jOOQ.
 */
@Suppress("UNCHECKED_CAST")
open class Golfer(
    alias: Name,
    path: Table<out Record>?,
    childPath: ForeignKey<out Record, GolferRecord>?,
    parentPath: InverseForeignKey<out Record, GolferRecord>?,
    aliased: Table<GolferRecord>?,
    parameters: Array<Field<*>?>?,
    where: Condition?
): TableImpl<GolferRecord>(
    alias,
    DefaultSchema.DEFAULT_SCHEMA,
    path,
    childPath,
    parentPath,
    aliased,
    parameters,
    DSL.comment(""),
    TableOptions.table(),
    where,
) {
    companion object {

        /**
         * The reference instance of <code>golfer</code>
         */
        val GOLFER: Golfer = Golfer()
    }

    /**
     * The class holding records for this type
     */
    override fun getRecordType(): Class<GolferRecord> = GolferRecord::class.java

    /**
     * The column <code>golfer.pin</code>.
     */
    val PIN: TableField<GolferRecord, String?> = createField(DSL.name("pin"), SQLDataType.VARCHAR(8).nullable(false), this, "")

    /**
     * The column <code>golfer.first_name</code>.
     */
    val FIRST_NAME: TableField<GolferRecord, String?> = createField(DSL.name("first_name"), SQLDataType.VARCHAR(32).nullable(false), this, "")

    /**
     * The column <code>golfer.last_name</code>.
     */
    val LAST_NAME: TableField<GolferRecord, String?> = createField(DSL.name("last_name"), SQLDataType.VARCHAR(32).nullable(false), this, "")

    /**
     * The column <code>golfer.handicap</code>.
     */
    val HANDICAP: TableField<GolferRecord, Float?> = createField(DSL.name("handicap"), SQLDataType.REAL.defaultValue(DSL.field(DSL.raw("0.0"), SQLDataType.REAL)), this, "")

    /**
     * The column <code>golfer.country</code>.
     */
    val COUNTRY: TableField<GolferRecord, String?> = createField(DSL.name("country"), SQLDataType.CHAR(2).nullable(false).defaultValue(DSL.field(DSL.raw("'US'::bpchar"), SQLDataType.CHAR)), this, "")

    /**
     * The column <code>golfer.state</code>.
     */
    val STATE: TableField<GolferRecord, String?> = createField(DSL.name("state"), SQLDataType.CHAR(2).nullable(false).defaultValue(DSL.field(DSL.raw("'NV'::bpchar"), SQLDataType.CHAR)), this, "")

    /**
     * The column <code>golfer.overlap_years</code>.
     */
    val OVERLAP_YEARS: TableField<GolferRecord, Boolean?> = createField(DSL.name("overlap_years"), SQLDataType.BOOLEAN.defaultValue(DSL.field(DSL.raw("false"), SQLDataType.BOOLEAN)), this, "")

    /**
     * The column <code>golfer.public</code>.
     */
    val PUBLIC: TableField<GolferRecord, Boolean?> = createField(DSL.name("public"), SQLDataType.BOOLEAN.defaultValue(DSL.field(DSL.raw("false"), SQLDataType.BOOLEAN)), this, "")

    /**
     * The column <code>golfer.last_login</code>.
     */
    val LAST_LOGIN: TableField<GolferRecord, Long?> = createField(DSL.name("last_login"), SQLDataType.BIGINT, this, "")

    private constructor(alias: Name, aliased: Table<GolferRecord>?): this(alias, null, null, null, aliased, null, null)
    private constructor(alias: Name, aliased: Table<GolferRecord>?, parameters: Array<Field<*>?>?): this(alias, null, null, null, aliased, parameters, null)
    private constructor(alias: Name, aliased: Table<GolferRecord>?, where: Condition?): this(alias, null, null, null, aliased, null, where)

    /**
     * Create an aliased <code>golfer</code> table reference
     */
    constructor(alias: String): this(DSL.name(alias))

    /**
     * Create an aliased <code>golfer</code> table reference
     */
    constructor(alias: Name): this(alias, null)

    /**
     * Create a <code>golfer</code> table reference
     */
    constructor(): this(DSL.name("golfer"), null)

    constructor(path: Table<out Record>, childPath: ForeignKey<out Record, GolferRecord>?, parentPath: InverseForeignKey<out Record, GolferRecord>?): this(Internal.createPathAlias(path, childPath, parentPath), path, childPath, parentPath, GOLFER, null, null)

    /**
     * A subtype implementing {@link Path} for simplified path-based joins.
     */
    open class GolferPath : Golfer, Path<GolferRecord> {
        constructor(path: Table<out Record>, childPath: ForeignKey<out Record, GolferRecord>?, parentPath: InverseForeignKey<out Record, GolferRecord>?): super(path, childPath, parentPath)
        private constructor(alias: Name, aliased: Table<GolferRecord>): super(alias, aliased)
        override fun `as`(alias: String): GolferPath = GolferPath(DSL.name(alias), this)
        override fun `as`(alias: Name): GolferPath = GolferPath(alias, this)
        override fun `as`(alias: Table<*>): GolferPath = GolferPath(alias.qualifiedName, this)
    }
    override fun getSchema(): Schema? = if (aliased()) null else DefaultSchema.DEFAULT_SCHEMA
    override fun getPrimaryKey(): UniqueKey<GolferRecord> = GOLFER_PKEY
    override fun getUniqueKeys(): List<UniqueKey<GolferRecord>> = listOf(GOLFER_NAMES_UNIQUE)

    private lateinit var _scores: ScoresPath

    /**
     * Get the implicit to-many join path to the <code>public.scores</code>
     * table
     */
    fun scores(): ScoresPath {
        if (!this::_scores.isInitialized)
            _scores = ScoresPath(this, null, SCORES__FK_GOLFER_SCORES.inverseKey)

        return _scores;
    }

    val scores: ScoresPath
        get(): ScoresPath = scores()
    override fun `as`(alias: String): Golfer = Golfer(DSL.name(alias), this)
    override fun `as`(alias: Name): Golfer = Golfer(alias, this)
    override fun `as`(alias: Table<*>): Golfer = Golfer(alias.qualifiedName, this)

    /**
     * Rename this table
     */
    override fun rename(name: String): Golfer = Golfer(DSL.name(name), null)

    /**
     * Rename this table
     */
    override fun rename(name: Name): Golfer = Golfer(name, null)

    /**
     * Rename this table
     */
    override fun rename(name: Table<*>): Golfer = Golfer(name.qualifiedName, null)

    /**
     * Create an inline derived table from this table
     */
    override fun where(condition: Condition?): Golfer = Golfer(qualifiedName, if (aliased()) this else null, condition)

    /**
     * Create an inline derived table from this table
     */
    override fun where(conditions: Collection<Condition>): Golfer = where(DSL.and(conditions))

    /**
     * Create an inline derived table from this table
     */
    override fun where(vararg conditions: Condition?): Golfer = where(DSL.and(*conditions))

    /**
     * Create an inline derived table from this table
     */
    override fun where(condition: Field<Boolean?>?): Golfer = where(DSL.condition(condition))

    /**
     * Create an inline derived table from this table
     */
    @PlainSQL override fun where(condition: SQL): Golfer = where(DSL.condition(condition))

    /**
     * Create an inline derived table from this table
     */
    @PlainSQL override fun where(@Stringly.SQL condition: String): Golfer = where(DSL.condition(condition))

    /**
     * Create an inline derived table from this table
     */
    @PlainSQL override fun where(@Stringly.SQL condition: String, vararg binds: Any?): Golfer = where(DSL.condition(condition, *binds))

    /**
     * Create an inline derived table from this table
     */
    @PlainSQL override fun where(@Stringly.SQL condition: String, vararg parts: QueryPart): Golfer = where(DSL.condition(condition, *parts))

    /**
     * Create an inline derived table from this table
     */
    override fun whereExists(select: Select<*>): Golfer = where(DSL.exists(select))

    /**
     * Create an inline derived table from this table
     */
    override fun whereNotExists(select: Select<*>): Golfer = where(DSL.notExists(select))
}
