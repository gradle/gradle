/*
 * Copyright 2019 the original author or authors.
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

import it.unimi.dsi.fastutil.ints.Int2ReferenceOpenHashMap
import it.unimi.dsi.fastutil.objects.ObjectArrayList
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet


class WriteIdentities {

    private
    val instanceIds = Reference2IntOpenHashMap<Any>()

    /**
     * Records the instances registered since [mark] so they can be removed by [restoreToMark].
     * Non-null only while a rollback scope is active. Nesting is not supported.
     */
    private
    var journal: ObjectArrayList<Any>? = null

    /**
     * Returns `-1` when the instance cannot be found to avoid boxing.
     */
    fun getId(instance: Any): Int =
        instanceIds.getOrDefault(instance, -1)

    fun putInstance(instance: Any): Int {
        val id = instanceIds.size
        instanceIds.put(instance, id)
        journal?.add(instance)
        return id
    }

    /**
     * Starts recording newly registered instances so a later [restoreToMark] can undo them.
     * Must be paired with exactly one [discardMark] (commit) or [restoreToMark] (rollback).
     */
    fun mark() {
        require(journal == null) { "WriteIdentities is already marked; nested rollback scopes are not supported." }
        journal = ObjectArrayList()
    }

    /**
     * Keeps all instances registered since [mark] and stops recording (commit).
     */
    fun discardMark() {
        require(journal != null) { "WriteIdentities is not marked." }
        journal = null
    }

    /**
     * Removes every instance registered since [mark] and stops recording (rollback).
     *
     * Ids are assigned densely from [instanceIds]`.size`, so removing exactly the instances added
     * since the mark restores both the contents and the next id to their state at [mark].
     */
    fun restoreToMark() {
        val recorded = journal
        require(recorded != null) { "WriteIdentities is not marked." }
        for (i in recorded.indices.reversed()) {
            instanceIds.removeInt(recorded[i])
        }
        journal = null
    }
}


class ReadIdentities {

    private
    val instanceIds = Int2ReferenceOpenHashMap<Any>()

    fun getInstance(id: Int): Any? =
        instanceIds.get(id)

    fun putInstance(id: Int, instance: Any) {
        instanceIds.put(id, instance)
    }
}


class CircularReferences {

    private
    val refs = ReferenceOpenHashSet<Any>()

    fun enter(reference: Any) {
        require(refs.add(reference))
    }

    operator fun contains(reference: Any) =
        refs.contains(reference)

    fun leave(reference: Any) {
        require(refs.remove(reference))
    }
}
