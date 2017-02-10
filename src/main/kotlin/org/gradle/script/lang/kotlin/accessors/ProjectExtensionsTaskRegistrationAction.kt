/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.script.lang.kotlin.accessors

import org.gradle.api.Project
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.configuration.project.ProjectConfigureAction
import org.gradle.script.lang.kotlin.accessors.ProjectExtensionsBuildSrcConfigurationAction.Companion.PROJECT_SCHEMA_RESOURCE_PATH
import org.gradle.script.lang.kotlin.invoke


class ProjectExtensionsTaskRegistrationAction : ProjectConfigureAction {

    override fun execute(project: ProjectInternal) {
        with (project) {
            if (isRootProjectOfBuildContainingKotlinBuildScripts()) {
                tasks {
                    "gskGenerateAccessors"(GenerateProjectSchema::class) {
                        destinationFile = file("buildSrc/$PROJECT_SCHEMA_RESOURCE_PATH")
                    }
                }
            }
            if (hasKotlinBuildScript()) {
                tasks {
                    "gskProjectAccessors"(DisplayAccessors::class)
                }
            }
        }
    }

    private fun ProjectInternal.isRootProjectOfBuildContainingKotlinBuildScripts() =
        this == rootProject && allprojects.any { it.hasKotlinBuildScript() }

    private fun Project.hasKotlinBuildScript() =
        buildFile?.path?.endsWith(".kts") ?: false
}
