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

import org.gradle.api.internal.BuildScopeListenerRegistrationListener
import org.gradle.internal.service.scopes.ListenerService
import org.gradle.internal.service.scopes.Scope
import org.gradle.internal.service.scopes.ServiceScope


/**
 * Flags this build's [ConfigurationCacheFingerprintController] when user code
 * registers any task-graph listener — `whenReady`, `addTaskExecutionGraphListener`,
 * and the deprecated `beforeTask` / `afterTask` / `addTaskExecutionListener` — so
 * the resulting cache entry is recorded as superset-ineligible.
 * <p>
 * Hooks into the existing [BuildScopeListenerRegistrationListener] broadcast
 * which fires from `DefaultTaskExecutionGraph.notifyListenerRegistration`.
 * `DefaultTaskExecutionGraph` calls `notifyListenerRegistration` from every
 * user-facing registration entry point, so this listener catches them all.
 */
@ListenerService
@ServiceScope(Scope.Build::class)
internal
class TaskGraphListenerRegistrationTracker(
    private val controller: ConfigurationCacheFingerprintController
) : BuildScopeListenerRegistrationListener {

    override fun onBuildScopeListenerRegistration(
        listener: Any,
        invocationDescription: String,
        invocationSource: Any
    ) {
        // Any user-code listener registration on the task graph implies the build
        // is making graph-dependent decisions. Note: the BuildScopeListenerRegistrationListener
        // event is only fired from notifyListenerRegistration, which skips
        // [org.gradle.internal.InternalListener]-marked listeners — so internal Gradle
        // listeners (including CC's own) never trip this path.
        controller.taskGraphAccessed()
    }
}
