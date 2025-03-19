/*
 * Copyright 2024 the original author or authors.
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

import org.gradle.cache.internal.CompositeCleanupAction
import org.gradle.cache.internal.DefaultCleanupProgressMonitor
import org.gradle.cache.internal.UnconditionalCacheCleanup
import org.gradle.internal.event.ListenerManager
import org.gradle.internal.file.FileAccessTimeJournal
import org.gradle.internal.operations.BuildOperationContext
import org.gradle.internal.operations.BuildOperationDescriptor
import org.gradle.internal.operations.BuildOperationRunner
import org.gradle.internal.operations.RunnableBuildOperation
import org.gradle.internal.service.scopes.Scope
import org.gradle.internal.service.scopes.ServiceScope
import org.gradle.internal.session.BuildSessionLifecycleListener


/**
 * Deletes evicted Configuration Cache entries at the end of the build session.
 */
@ServiceScope(Scope.BuildSession::class)
internal
class ConfigurationCacheEntryCollector(
    private val repository: ConfigurationCacheRepository,
    private val journal: FileAccessTimeJournal,
    private val buildOperationRunner: BuildOperationRunner,
    listenerManager: ListenerManager
) {

    init {
        listenerManager.addListener(object : BuildSessionLifecycleListener {
            override fun beforeComplete() {
                performCollection()
            }
        })
    }

    fun scheduleForCollection(evictedEntry: String) {
        evictedEntries.add(evictedEntry)
    }

    private
    val evictedEntries = mutableSetOf<String>()

    private
    fun performCollection() {
        buildOperationRunner.run(object : RunnableBuildOperation {
            override fun description(): BuildOperationDescriptor.Builder =
                BuildOperationDescriptor
                    .displayName("Configuration Cache Collection")
                    .progressDisplayName("Collecting evicted configuration cache entries...")

            override fun run(context: BuildOperationContext) {
                repository.withExclusiveCleanupAccess {
                    applyCleanupAction(
                        composeCleanupActionForEntries(),
                        DefaultCleanupProgressMonitor(context)
                    )
                }
            }
        })
    }

    private
    fun ConfigurationCacheRepository.CleanupContext.composeCleanupActionForEntries(): CompositeCleanupAction =
        UnconditionalCacheCleanup(eligibleFilesFinder, journal).let { entryCleanup ->
            CompositeCleanupAction.builder().run {
                evictedEntries.forEach { entry ->
                    add(dirForEntry(entry), entryCleanup)
                }
                build()
            }
        }
}
