/*
 * Copyright 2021 the original author or authors.
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
package org.gradle.configurationcache

import org.gradle.api.internal.GradleInternal
import org.gradle.api.internal.SettingsInternal
import org.gradle.internal.build.BuildModelController


class ConfigurationCacheAwareBuildModelController(
    private val model: GradleInternal,
    private val delegate: BuildModelController,
    private val configurationCache: ConfigurationCache
) : BuildModelController {
    private
    enum class State {
        Created, Loaded, DoNotReuse
    }

    private
    var state = State.Created

    override fun getLoadedSettings(): SettingsInternal {
        return if (maybeLoadFromCache()) {
            model.settings
        } else {
            delegate.loadedSettings
        }
    }

    override fun getConfiguredModel(): GradleInternal {
        return if (maybeLoadFromCache()) {
            throw IllegalStateException("Cannot query configured model when model has been loaded from configuration cache.")
        } else {
            delegate.configuredModel
        }
    }

    override fun scheduleTasks(tasks: Iterable<String>) {
        check(!maybeLoadFromCache()) { "Cannot schedule specific tasks when model has been loaded from configuration cache." }
        delegate.scheduleTasks(tasks)
    }

    override fun scheduleRequestedTasks() {
        if (!maybeLoadFromCache()) {
            delegate.scheduleRequestedTasks()
            configurationCache.save()
        } // Else, already scheduled
    }

    private
    fun maybeLoadFromCache(): Boolean {
        synchronized(this) {
            return when (state) {
                State.Created ->
                    if (configurationCache.canLoad()) {
                        configurationCache.load()
                        state = State.Loaded
                        true
                    } else {
                        configurationCache.prepareForConfiguration()
                        state = State.DoNotReuse
                        false
                    }
                State.Loaded -> true
                State.DoNotReuse -> false
            }
        }
    }
}
