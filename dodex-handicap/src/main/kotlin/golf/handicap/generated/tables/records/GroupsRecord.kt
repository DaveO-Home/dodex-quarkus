/*
 * This file is generated by jOOQ.
 */
package golf.handicap.generated.tables.records


import golf.handicap.generated.tables.Groups

import java.time.OffsetDateTime

import org.jooq.Record1
import org.jooq.impl.UpdatableRecordImpl


/**
 * This class is generated by jOOQ.
 */
@Suppress("UNCHECKED_CAST")
open class GroupsRecord() : UpdatableRecordImpl<GroupsRecord>(Groups.GROUPS) {

    open var id: Int?
        set(value): Unit = set(0, value)
        get(): Int? = get(0) as Int?

    open var name: String?
        set(value): Unit = set(1, value)
        get(): String? = get(1) as String?

    open var owner: Int?
        set(value): Unit = set(2, value)
        get(): Int? = get(2) as Int?

    open var created: OffsetDateTime?
        set(value): Unit = set(3, value)
        get(): OffsetDateTime? = get(3) as OffsetDateTime?

    open var updated: OffsetDateTime?
        set(value): Unit = set(4, value)
        get(): OffsetDateTime? = get(4) as OffsetDateTime?

    // -------------------------------------------------------------------------
    // Primary key information
    // -------------------------------------------------------------------------

    override fun key(): Record1<Int?> = super.key() as Record1<Int?>

    /**
     * Create a detached, initialised GroupsRecord
     */
    constructor(id: Int? = null, name: String? = null, owner: Int? = null, created: OffsetDateTime? = null, updated: OffsetDateTime? = null): this() {
        this.id = id
        this.name = name
        this.owner = owner
        this.created = created
        this.updated = updated
        resetChangedOnNotNull()
    }
}
