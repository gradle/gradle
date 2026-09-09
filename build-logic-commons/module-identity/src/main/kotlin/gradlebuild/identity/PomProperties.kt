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

package gradlebuild.identity

import gradlebuild.identity.extension.GradleModuleExtension
import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider
import org.gradle.pomproperties.GeneratePomProperties

/**
 * Registers a [GeneratePomProperties] task (the task type comes from the `org.gradle.pom-properties`
 * plugin) for a distribution jar whose artifactId is not the module default the plugin convention
 * handles - namely the public-API ABI jar and the synthesized metadata jars.
 */
fun Project.registerPomPropertiesTask(
    taskName: String,
    artifactId: String
): TaskProvider<GeneratePomProperties> {
    val identity = extensions.getByType(GradleModuleExtension::class.java).identity
    return tasks.register(taskName, GeneratePomProperties::class.java) {
        groupId.set(identity.group)
        this.artifactId.set(artifactId)
        version.set(identity.reproducibleVersion)
        destinationDirectory.set(layout.buildDirectory.dir("generated-resources/$taskName"))
    }
}
