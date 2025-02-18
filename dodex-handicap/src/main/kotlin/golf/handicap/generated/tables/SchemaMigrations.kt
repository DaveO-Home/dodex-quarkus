/*
 * This file is generated by jOOQ.
 */
package golf.handicap.generated.tables


import golf.handicap.generated.DefaultSchema
import golf.handicap.generated.tables.records.SchemaMigrationsRecord

import kotlin.collections.Collection

import org.jooq.Condition
import org.jooq.Field
import org.jooq.ForeignKey
import org.jooq.InverseForeignKey
import org.jooq.Name
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
import org.jooq.impl.DSL
import org.jooq.impl.SQLDataType
import org.jooq.impl.TableImpl


/**
 * This class is generated by jOOQ.
 */
@Suppress("UNCHECKED_CAST")
open class SchemaMigrations(
    alias: Name,
    path: Table<out Record>?,
    childPath: ForeignKey<out Record, SchemaMigrationsRecord>?,
    parentPath: InverseForeignKey<out Record, SchemaMigrationsRecord>?,
    aliased: Table<SchemaMigrationsRecord>?,
    parameters: Array<Field<*>?>?,
    where: Condition?
): TableImpl<SchemaMigrationsRecord>(
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
         * The reference instance of <code>schema_migrations</code>
         */
        val SCHEMA_MIGRATIONS: SchemaMigrations = SchemaMigrations()
    }

    /**
     * The class holding records for this type
     */
    override fun getRecordType(): Class<SchemaMigrationsRecord> = SchemaMigrationsRecord::class.java

    /**
     * The column <code>schema_migrations.version</code>.
     */
    val VERSION: TableField<SchemaMigrationsRecord, String?> = createField(DSL.name("version"), SQLDataType.VARCHAR(30), this, "")

    private constructor(alias: Name, aliased: Table<SchemaMigrationsRecord>?): this(alias, null, null, null, aliased, null, null)
    private constructor(alias: Name, aliased: Table<SchemaMigrationsRecord>?, parameters: Array<Field<*>?>?): this(alias, null, null, null, aliased, parameters, null)
    private constructor(alias: Name, aliased: Table<SchemaMigrationsRecord>?, where: Condition?): this(alias, null, null, null, aliased, null, where)

    /**
     * Create an aliased <code>schema_migrations</code> table reference
     */
    constructor(alias: String): this(DSL.name(alias))

    /**
     * Create an aliased <code>schema_migrations</code> table reference
     */
    constructor(alias: Name): this(alias, null)

    /**
     * Create a <code>schema_migrations</code> table reference
     */
    constructor(): this(DSL.name("schema_migrations"), null)
    override fun getSchema(): Schema? = if (aliased()) null else DefaultSchema.DEFAULT_SCHEMA
    override fun `as`(alias: String): SchemaMigrations = SchemaMigrations(DSL.name(alias), this)
    override fun `as`(alias: Name): SchemaMigrations = SchemaMigrations(alias, this)
    override fun `as`(alias: Table<*>): SchemaMigrations = SchemaMigrations(alias.qualifiedName, this)

    /**
     * Rename this table
     */
    override fun rename(name: String): SchemaMigrations = SchemaMigrations(DSL.name(name), null)

    /**
     * Rename this table
     */
    override fun rename(name: Name): SchemaMigrations = SchemaMigrations(name, null)

    /**
     * Rename this table
     */
    override fun rename(name: Table<*>): SchemaMigrations = SchemaMigrations(name.qualifiedName, null)

    /**
     * Create an inline derived table from this table
     */
    override fun where(condition: Condition?): SchemaMigrations = SchemaMigrations(qualifiedName, if (aliased()) this else null, condition)

    /**
     * Create an inline derived table from this table
     */
    override fun where(conditions: Collection<Condition>): SchemaMigrations = where(DSL.and(conditions))

    /**
     * Create an inline derived table from this table
     */
    override fun where(vararg conditions: Condition?): SchemaMigrations = where(DSL.and(*conditions))

    /**
     * Create an inline derived table from this table
     */
    override fun where(condition: Field<Boolean?>?): SchemaMigrations = where(DSL.condition(condition))

    /**
     * Create an inline derived table from this table
     */
    @PlainSQL override fun where(condition: SQL): SchemaMigrations = where(DSL.condition(condition))

    /**
     * Create an inline derived table from this table
     */
    @PlainSQL override fun where(@Stringly.SQL condition: String): SchemaMigrations = where(DSL.condition(condition))

    /**
     * Create an inline derived table from this table
     */
    @PlainSQL override fun where(@Stringly.SQL condition: String, vararg binds: Any?): SchemaMigrations = where(DSL.condition(condition, *binds))

    /**
     * Create an inline derived table from this table
     */
    @PlainSQL override fun where(@Stringly.SQL condition: String, vararg parts: QueryPart): SchemaMigrations = where(DSL.condition(condition, *parts))

    /**
     * Create an inline derived table from this table
     */
    override fun whereExists(select: Select<*>): SchemaMigrations = where(DSL.exists(select))

    /**
     * Create an inline derived table from this table
     */
    override fun whereNotExists(select: Select<*>): SchemaMigrations = where(DSL.notExists(select))
}
