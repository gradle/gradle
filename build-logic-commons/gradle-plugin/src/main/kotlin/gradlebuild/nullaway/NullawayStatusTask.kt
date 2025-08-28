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

package gradlebuild.nullaway

import org.gradle.api.DefaultTask
import org.gradle.api.artifacts.ArtifactCollection
import org.gradle.api.artifacts.result.ResolvedArtifactResult
import org.gradle.api.provider.Property
import org.gradle.api.services.ServiceReference
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.registerIfAbsent
import org.gradle.language.base.plugins.LifecycleBasePlugin

@Suppress("UnstableApiUsage")
internal abstract class NullawayStatusTask : DefaultTask() {
    @get:ServiceReference
    abstract val statusService: Property<NullawayStatusService>

    @get:Input
    val projectPath = project.buildTreePath

    @get:Input
    abstract val nullawayEnabled: Property<Boolean>

    // TODO(https://github.com/gradle/gradle/issues/27582): We cannot use a SetProperty<ResolvedArtifactResult> because some projects
    //  have no task dependencies and thus are non-CC serializable.
    @get:Input
    abstract val nullawayAwareDeps: Property<ArtifactCollection>

    init {
        project.gradle.sharedServices.registerIfAbsent(NullawayStatusService.SERVICE_NAME, NullawayStatusService::class)
        group = LifecycleBasePlugin.BUILD_GROUP
        description = "Checks status of NullAway support in this project. Call unqualified to get summary for all projects."
    }

    @TaskAction
    fun action() {
        val nullawayIncompatibleDeps = nullawayAwareDeps.get().filter { it.hasNullAwayDisabled }
        val service = statusService.get()
        when {
            nullawayEnabled.get() -> service.addProjectWithNullawayEnabled(projectPath)
            nullawayIncompatibleDeps.isEmpty() -> service.addProjectToEnableNullawayIn(projectPath)
            else -> service.addProjectWithUncheckedDeps(projectPath)
        }
    }

    private val ResolvedArtifactResult.hasNullAwayDisabled
        get() = variant.attributes.getAttribute(NullawayAttributes.nullawayAttribute) == NullawayState.DISABLED
}
