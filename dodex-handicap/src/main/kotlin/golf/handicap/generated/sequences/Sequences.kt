/*
 * This file is generated by jOOQ.
 */
package golf.handicap.generated.sequences


import golf.handicap.generated.DefaultSchema

import org.jooq.Sequence
import org.jooq.impl.Internal
import org.jooq.impl.SQLDataType



/**
 * The sequence <code>course_id_seq</code>
 */
val COURSE_ID_SEQ: Sequence<Long> = Internal.createSequence("course_id_seq", DefaultSchema.DEFAULT_SCHEMA, SQLDataType.BIGINT.nullable(false), null, null, null, 2147483647, false, null)
