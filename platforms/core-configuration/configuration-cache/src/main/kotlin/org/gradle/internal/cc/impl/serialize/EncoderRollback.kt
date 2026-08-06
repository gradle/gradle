/*
 * Copyright 2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.internal.cc.impl.serialize

import org.gradle.internal.serialize.FlushableEncoder
import org.gradle.internal.serialize.graph.WriteRollback


/**
 * Ties an [encoder] to the [SpillingOutputStream] underneath it to implement byte-level rollback.
 *
 * On every boundary the encoder is flushed so its buffered tail reaches the spill before the spill
 * is committed or dropped. Rolling back flushes the tail into the spool too and then discards the
 * whole spool, so it never needs to touch the encoder's internal buffer.
 */
internal
class EncoderRollback(
    private val encoder: FlushableEncoder,
    private val spilling: SpillingOutputStream
) : WriteRollback {

    override fun beginScope() {
        encoder.flush()
        spilling.beginSpill()
    }

    override fun commitScope() {
        encoder.flush()
        spilling.commit()
    }

    override fun rollbackScope() {
        encoder.flush()
        spilling.rollback()
    }
}
