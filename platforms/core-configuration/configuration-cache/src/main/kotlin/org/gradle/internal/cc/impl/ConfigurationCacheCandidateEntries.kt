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

package org.gradle.internal.cc.impl

import org.gradle.internal.cc.base.serialize.HostServiceProvider
import org.gradle.internal.cc.base.serialize.service
import org.gradle.internal.cc.operations.EntrySearchResult


internal class ConfigurationCacheCandidateEntries(
    private val store: ConfigurationCacheStateStore,
    private val cacheIO: ConfigurationCacheBuildTreeIO,
    private val entriesPerKey: Int,
    private val host: HostServiceProvider
) {

    fun searchForValidEntry(checkCandidate: (CandidateEntry) -> EntrySearchResult): EntrySearchResult {
        var firstInvalidResult: EntrySearchResult? = null
        for (candidate in load()) {
            val result = checkCandidate(candidate)
            when (result.checkedFingerprint) {
                is CheckedFingerprint.Valid -> {
                    return result
                }

                is CheckedFingerprint.Invalid -> {
                    if (firstInvalidResult == null) {
                        firstInvalidResult = result
                    }
                }

                CheckedFingerprint.NotFound -> continue
            }
        }
        return firstInvalidResult
            ?: EntrySearchResult(null, CheckedFingerprint.NotFound)
    }

    fun markMostRecentlyUsed(entryId: String) = update {
        withMostRecentEntry(CandidateEntry(entryId))
    }

    fun remove(entry: CandidateEntry) = update {
        minus(entry)
    }

    private
    fun load(): List<CandidateEntry> = store.useForStateLoad {
        readCandidateEntries()
    }.value

    private
    fun update(update: List<CandidateEntry>.() -> List<CandidateEntry>) = store.useForStore {
        val existingEntries = readCandidateEntries()
        val newEntries = update(existingEntries)
        if (existingEntries != newEntries) {
            writeCandidateEntries(newEntries)
            scheduleForCollection(existingEntries - newEntries.toHashSet())
        }
    }

    private
    fun ConfigurationCacheRepository.Layout.readCandidateEntries() =
        cacheIO.readCandidateEntries(fileForRead(StateType.Candidates))

    private
    fun ConfigurationCacheRepository.Layout.writeCandidateEntries(entries: List<CandidateEntry>) {
        cacheIO.writeCandidateEntries(fileFor(StateType.Candidates), entries)
    }

    private
    fun List<CandidateEntry>.withMostRecentEntry(mostRecent: CandidateEntry): List<CandidateEntry> = when {
        isEmpty() -> listOf(mostRecent)
        first() == mostRecent -> this
        else -> buildList(entriesPerKey) {
            add(mostRecent)
            val remaining = entriesPerKey - 1
            if (remaining > 0) {
                addAll(
                    this@withMostRecentEntry.asSequence()
                        .filter { it != mostRecent }
                        .take(remaining)
                )
            }
        }
    }

    private
    fun scheduleForCollection(evictedEntries: List<CandidateEntry>) {
        if (evictedEntries.isNotEmpty()) {
            host.service<ConfigurationCacheEntryCollector>().let { collector ->
                evictedEntries.forEach { entry ->
                    collector.scheduleForCollection(entry.id)
                }
            }
        }
    }
}
