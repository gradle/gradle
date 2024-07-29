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

package org.gradle.internal.cc.impl

import org.gradle.internal.configuration.problems.StructuredMessage
import org.gradle.util.Path


sealed class CheckedFingerprint {
    // No fingerprint, which means no cache entry
    object NotFound : CheckedFingerprint()

    // Everything is up-to-date
    object Valid : CheckedFingerprint()

    // The entry cannot be reused at all and should be recreated from scratch
    class EntryInvalid(val buildPath: Path, val reason: StructuredMessage) : CheckedFingerprint()

    // The entry can be reused, however the values for certain projects cannot be reused and should be recreated
    class ProjectsInvalid(
        /**
         * Identity path of the first project for which an invalidation was detected.
         */
        val firstInvalidated: Path,
        /**
         * All invalidated projects with their invalidation reasons by identity path. Must contain [firstInvalidated].
         */
        val invalidProjects: Map<Path, ProjectInvalidationData>
    ) : CheckedFingerprint() {
        init {
            require(firstInvalidated in invalidProjects)
        }

        /**
         * The first invalidation reason detected.
         */
        val firstReason: StructuredMessage
            get() = invalidProjects.getValue(firstInvalidated).message
    }

    /**
     * Information about an invalidated project.
     */
    data class ProjectInvalidationData(val buildPath: Path, val projectPath: Path, val message: StructuredMessage)
}
