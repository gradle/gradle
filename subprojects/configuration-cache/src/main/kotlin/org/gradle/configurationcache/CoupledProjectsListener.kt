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

import org.gradle.api.internal.project.ProjectState
import org.gradle.internal.service.scopes.EventScope
import org.gradle.internal.service.scopes.Scopes


@EventScope(Scopes.Build::class)
interface CoupledProjectsListener {
    /**
     * Notified when the build logic for a [referrer] project accesses the mutable state of some other [target] project.
     *
     * The [referrer] and [target] might represent the same project, and the listener implementation
     * should handle this specifically, probably ignoring such calls, as a project is naturally coupled with itself.
     */
    fun onProjectReference(referrer: ProjectState, target: ProjectState)
}
