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

    /**
     * No fingerprint, which means no cache entry
     */
    object NotFound : CheckedFingerprint() {
        override fun toString(): String = "NotFound"
    }

    /**
     * The entry cannot be reused at all and should be recreated from scratch
     */
    data class Invalid(
        val buildPath: Path,
        val reason: StructuredMessage
    ) : CheckedFingerprint()

    /**
     * The entry can be reused. However, the state of some projects might be invalid and should be recreated.
     */
    // TODO:isolated when keeping multiple entries per key, Gradle should look for the entry with the least number of invalid projects
    data class Valid(
        val entryId: String,
        val invalidProjects: InvalidProjects? = null
    ) : CheckedFingerprint()

    /**
     * The entry can be reused, however the values for certain projects cannot be reused and should be recreated
     */
    data class InvalidProjects(
        /**
         * Identity path of the first project for which an invalidation was detected.
         */
        val firstProjectPath: Path,
        /**
         * All invalidated projects with their invalidation reasons by identity path. Must contain [firstProjectPath].
         */
        val all: Map<Path, InvalidProject>
    ) {
        init {
            require(firstProjectPath in all)
        }

        val size
            get() = all.size

        val first: InvalidProject
            get() = all.getValue(firstProjectPath)
    }

    /**
     * Information about an invalidated project.
     */
    data class InvalidProject(
        val buildPath: Path,
        val projectPath: Path,
        val reason: StructuredMessage
    )
}
