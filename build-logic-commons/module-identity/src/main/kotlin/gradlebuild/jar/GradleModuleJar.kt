/*
 * Copyright 2025 the original author or authors.
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

package gradlebuild.jar

import gradlebuild.identity.extension.GradleModuleExtension
import org.gradle.api.Project
import org.gradle.api.tasks.bundling.Jar
import org.gradle.kotlin.dsl.assign
import org.gradle.kotlin.dsl.the
import org.gradle.kotlin.dsl.withType
import java.util.jar.Attributes

fun Project.configureGradleModuleJarTasks() {
    val gradleModule = the<GradleModuleExtension>()
    tasks.withType<Jar>().configureEach {
        archiveBaseName = gradleModule.identity.baseName
        archiveVersion = gradleModule.identity.version.map { it.baseVersion.version }
        manifest.attributes(
            mapOf(
                Attributes.Name.IMPLEMENTATION_TITLE.toString() to "Gradle",
                Attributes.Name.IMPLEMENTATION_VERSION.toString() to gradleModule.identity.version.map { it.baseVersion.version }
            )
        )
    }
}
