/*
 * This file is generated by jOOQ.
 */
package golf.handicap.generated.tables.records


import golf.handicap.generated.tables.Member

import org.jooq.impl.TableRecordImpl


/**
 * This class is generated by jOOQ.
 */
@Suppress("UNCHECKED_CAST")
open class MemberRecord() : TableRecordImpl<MemberRecord>(Member.MEMBER) {

    open var groupId: Int?
        set(value): Unit = set(0, value)
        get(): Int? = get(0) as Int?

    open var userId: Int?
        set(value): Unit = set(1, value)
        get(): Int? = get(1) as Int?

    /**
     * Create a detached, initialised MemberRecord
     */
    constructor(groupId: Int? = null, userId: Int? = null): this() {
        this.groupId = groupId
        this.userId = userId
        resetChangedOnNotNull()
    }
}
