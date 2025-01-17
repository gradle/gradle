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

package org.gradle.internal.declarativedsl.provider

import org.gradle.BuildAdapter
import org.gradle.BuildListener
import org.gradle.api.initialization.Settings
import org.gradle.api.internal.GradleInternal
import org.gradle.api.internal.SettingsInternal
import org.gradle.internal.concurrent.Stoppable
import org.gradle.internal.event.ListenerManager
import org.gradle.internal.service.scopes.Scope
import org.gradle.internal.service.scopes.ServiceScope

/**
 * Captures an instance of [SettingsInternal] that is broadcast to [BuildListener] as early as [BuildListener.beforeSettings].
 * Used as a workaround to inability to use [GradleInternal.getSettings] while evaluating a settings script.
 */
@ServiceScope(Scope.Build::class)
internal class SettingsUnderInitialization(private val listenerManager: ListenerManager) : BuildAdapter(), Stoppable {
    val instance: SettingsInternal
        get() = if (::settings.isInitialized)
            settings
        else error("SettingsUnderInitialization.settings accessed too early, no settings object at this point yet")

    init {
        listenerManager.addListener(this)
    }

    override fun stop() {
        listenerManager.removeListener(this)
    }

    private lateinit var settings: SettingsInternal

    override fun beforeSettings(settings: Settings) {
        this.settings = settings as SettingsInternal
        listenerManager.removeListener(this)
    }
}
