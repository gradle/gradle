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

import org.gradle.api.Project
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.configurationcache.serialization.DefaultReadContext
import org.gradle.normalization.internal.InputNormalizationHandlerInternal
import java.lang.IllegalArgumentException


/**
 * The configuration data of the project stored in the configuration cache.
 */
internal
class CachedProjectState(
    private val path: String,
    private val normalizationState: InputNormalizationHandlerInternal.CachedState
) {
    companion object {
        fun DefaultReadContext.configureProjectFromCachedState(state: CachedProjectState) {
            val project = getProject(state.path)
            state.apply {
                project.normalization.configureFromCachedState(normalizationState)
            }
        }

        fun Project.computeCachedState(): CachedProjectState? {
            if (this !is ProjectInternal) {
                throw IllegalArgumentException("Cannot compute cached state for project '$path'")
            }
            val normalizationState = normalization.computeCachedState() ?: return null
            return CachedProjectState(path, normalizationState)
        }
    }
}
