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

package org.gradle.kotlin.dsl.support

import org.gradle.initialization.EnvironmentChangeTracker

/**
 * Provides an environment to safely run the Kotlin compilation.
 */
class KotlinCompilerEnvironment private constructor() {
    // We only want external code to reach out to an instance of this class through withEnvironment.

    companion object {
        private val instance = KotlinCompilerEnvironment()

        /**
         * Runs the action while properly tracking environment changes caused by compilation.
         */
        fun <T> withEnvironment(environmentChangeTracker: EnvironmentChangeTracker, action: KotlinCompilerEnvironment.() -> T): T {
            return environmentChangeTracker.withTrackingSystemPropertyChanges {
                instance.action()
            }
        }
    }
}
