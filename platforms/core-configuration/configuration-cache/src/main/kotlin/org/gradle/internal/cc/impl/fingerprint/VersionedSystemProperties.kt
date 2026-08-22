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

package org.gradle.internal.cc.impl.fingerprint

import org.gradle.internal.collect.PersistentMap
import java.util.Properties
import java.util.TreeMap


/**
 * The system properties as they were seen while a configuration cache entry was being stored, reconstructed
 * from the changes recorded in the entry.
 *
 * Build logic can change system properties while the build is configured, so the value a fingerprint entry
 * observed depends on when it was recorded. The entry records the changes, each with the version it produced,
 * and every value that observed the properties records the version it observed. Checking an entry then means
 * comparing a recorded value against [snapshotAt] its own version, which makes the check independent of the
 * order the fingerprint values happen to be visited in.
 *
 * Versions are dense: version 0 is the environment this process started the check with, and every subsequent
 * version is produced by exactly one recorded change. A version is only meaningful once every change up to it
 * has been [ingested][ingest], which [isReadyFor] reports.
 *
 * This class is not thread-safe: the check runs on a single thread.
 */
internal
class VersionedSystemProperties(initialProperties: Properties) {

    private
    sealed interface Change {

        fun applyTo(properties: PersistentMap<Any, Any>): PersistentMap<Any, Any>

        data class Set(val key: Any, val value: Any?) : Change {
            override fun applyTo(properties: PersistentMap<Any, Any>) =
                // A null value cannot be stored in the properties table, so treat it as a removal, the same way
                // the JVM does.
                if (value == null) properties.dissoc(key) else properties.assoc(key, value)
        }

        data class Remove(val key: Any) : Change {
            override fun applyTo(properties: PersistentMap<Any, Any>) = properties.dissoc(key)
        }

        object Clear : Change {
            override fun applyTo(properties: PersistentMap<Any, Any>) = PersistentMap.of<Any, Any>()
        }

        data class Install(val properties: Map<String, String>) : Change {
            override fun applyTo(properties: PersistentMap<Any, Any>) =
                this.properties.entries.fold(properties) { acc, (key, value) -> acc.assoc(key, value) }
        }
    }

    /**
     * The properties as of each version they changed in, most recent last. Only holds versions that are
     * contiguous with version 0, see [materializePending].
     */
    private
    val snapshots = TreeMap<Long, PersistentMap<Any, Any>>().apply {
        put(0L, PersistentMap.copyOf<Any, Any>(initialProperties.entries))
    }

    /**
     * Changes that cannot be applied yet because an earlier version hasn't been ingested.
     */
    private
    val pendingChanges = TreeMap<Long, Change>()

    /**
     * The highest version whose changes, and all changes before it, have been applied to [snapshots].
     */
    private
    var materializedUpTo = 0L

    fun setProperty(version: Long, key: Any, value: Any?) = ingest(version, Change.Set(key, value))

    fun removeProperty(version: Long, key: Any) = ingest(version, Change.Remove(key))

    fun clearProperties(version: Long) = ingest(version, Change.Clear)

    /**
     * Records the system properties that loading Gradle properties installed. These are part of the
     * environment rather than a change made by the build logic, but they still happen at a point in time
     * that the values recorded around them depend on.
     */
    fun installProperties(version: Long, properties: Map<String, String>) = ingest(version, Change.Install(properties))

    /**
     * Whether [snapshotAt] can answer for the given version, i.e. every change up to it has been ingested.
     */
    fun isReadyFor(version: Long): Boolean = version <= materializedUpTo

    /**
     * The properties as of the given version.
     *
     * Only meaningful once [isReadyFor] holds for that version, except after [ingestionFinished], which
     * settles whatever is left and makes this fall back to the closest preceding version that is known.
     */
    fun snapshotAt(version: Long): PersistentMap<Any, Any> = snapshots.floorEntry(version).value

    /**
     * The properties after all recorded changes, i.e. as the build that stored the entry left them.
     */
    fun finalSnapshot(): PersistentMap<Any, Any> = snapshots.lastEntry().value

    /**
     * Applies whatever changes are still pending, in version order.
     *
     * Changes should never be left pending: a value recorded at version N implies that versions 1 to N were
     * all assigned, and so were all recorded in the same file. Settling them anyway keeps a fingerprint that
     * violates this from being checked against an incomplete view of the properties. This happens for values
     * copied from a previous entry when only some projects are reused, where the recorded versions belong to
     * the previous build altogether.
     */
    fun ingestionFinished() {
        while (pendingChanges.isNotEmpty()) {
            val (version, change) = pendingChanges.pollFirstEntry()
            putSnapshot(version, change)
        }
    }

    private
    fun ingest(version: Long, change: Change) {
        // A version that is already materialized cannot be applied in the right place anymore. This only
        // happens for a fingerprint that violates the density invariant, which isn't worth failing the check
        // over: leave it pending, so that it is settled by ingestionFinished at worst.
        pendingChanges[version] = change
        materializePending()
    }

    private
    fun materializePending() {
        while (true) {
            val next = pendingChanges.remove(materializedUpTo + 1) ?: return
            putSnapshot(materializedUpTo + 1, next)
            materializedUpTo += 1
        }
    }

    private
    fun putSnapshot(version: Long, change: Change) {
        snapshots[version] = change.applyTo(snapshots.lastEntry().value)
    }
}


/**
 * The given properties as a table that can be handed to [System.setProperties].
 */
internal
fun PersistentMap<Any, Any>.toProperties(): Properties = Properties().also { properties ->
    forEach { (key, value) -> properties[key] = value }
}
