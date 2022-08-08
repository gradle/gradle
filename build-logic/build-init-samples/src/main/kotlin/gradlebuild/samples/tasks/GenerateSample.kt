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

package gradlebuild.samples.tasks

import gradlebuild.samples.SamplesGenerator

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.buildinit.plugins.internal.ProjectLayoutSetupRegistry
import org.gradle.buildinit.plugins.internal.modifiers.ModularizationOption
import org.gradle.work.DisableCachingByDefault
import javax.inject.Inject


@DisableCachingByDefault(because = "Not worth caching")
abstract class GenerateSample : DefaultTask() {

    @get:Input
    abstract val type: Property<String>

    @get:Input
    abstract val modularization: Property<ModularizationOption>

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val readmeTemplates: DirectoryProperty

    @get:OutputDirectory
    abstract val target: DirectoryProperty

    @get:Inject
    protected
    abstract val projectLayoutRegistry: ProjectLayoutSetupRegistry

    @TaskAction
    fun setupProjectLayout() {
        val projectLayoutSetupRegistry = projectLayoutRegistry
        SamplesGenerator.generate(type.get(), modularization.get(), readmeTemplates.get(), target.get(), projectLayoutSetupRegistry)
    }
}
