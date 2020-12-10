/*
 * Copyright 2020 the original author or authors.
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

package gradlebuild.jvm.extension

import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.TaskContainer
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.kotlin.dsl.*


abstract class UnitTestAndCompileExtension(private val tasks: TaskContainer) {

    fun usedInWorkers() {
        enforceJava6Compatibility()
    }

    fun usedForStartup() {
        enforceJava6Compatibility()
    }

    fun usedInToolingApi() {
        enforceJava6Compatibility()
    }

    fun enforceJava6Compatibility() {
        tasks.withType<JavaCompile>().configureEach {
            options.release.set(null)
            sourceCompatibility = "6"
            targetCompatibility = "6"
        }
    }
}
