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

package org.gradle.internal.serialize.graph

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class WriteIdentitiesTest {

    @Test
    fun `assigns dense ids in registration order`() {
        val identities = WriteIdentities()
        val a = Any()
        val b = Any()

        assertEquals(0, identities.putInstance(a))
        assertEquals(1, identities.putInstance(b))
        assertEquals(0, identities.getId(a))
        assertEquals(1, identities.getId(b))
        assertEquals(-1, identities.getId(Any()))
    }

    @Test
    fun `restoreToMark removes instances registered since mark and rewinds the id counter`() {
        val identities = WriteIdentities()
        val beforeMark = Any()
        identities.putInstance(beforeMark)

        identities.mark()
        val sinceMark1 = Any()
        val sinceMark2 = Any()
        identities.putInstance(sinceMark1)
        identities.putInstance(sinceMark2)

        identities.restoreToMark()

        // instances registered since the mark are gone
        assertEquals(-1, identities.getId(sinceMark1))
        assertEquals(-1, identities.getId(sinceMark2))
        // instances registered before the mark keep their ids
        assertEquals(0, identities.getId(beforeMark))
        // the next id continues from the mark, as if the rolled-back instances never existed
        assertEquals(1, identities.putInstance(Any()))
    }

    @Test
    fun `discardMark keeps instances registered since mark`() {
        val identities = WriteIdentities()
        identities.mark()
        val instance = Any()
        assertEquals(0, identities.putInstance(instance))

        identities.discardMark()

        assertEquals(0, identities.getId(instance))
        assertEquals(1, identities.putInstance(Any()))
    }

    @Test
    fun `mark rejects nesting`() {
        val identities = WriteIdentities()
        identities.mark()
        assertThrows(IllegalArgumentException::class.java) { identities.mark() }
    }

    @Test
    fun `commit and rollback require an active mark`() {
        val identities = WriteIdentities()
        assertThrows(IllegalArgumentException::class.java) { identities.restoreToMark() }
        assertThrows(IllegalArgumentException::class.java) { identities.discardMark() }
    }
}
