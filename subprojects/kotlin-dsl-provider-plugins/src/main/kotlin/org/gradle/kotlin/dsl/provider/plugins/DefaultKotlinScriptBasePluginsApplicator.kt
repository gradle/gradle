/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.kotlin.dsl.provider.plugins

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.kotlin.dsl.accessors.tasks.PrintAccessors
import org.gradle.kotlin.dsl.accessors.warnAboutDiscontinuedJsonProjectSchema
import org.gradle.kotlin.dsl.provider.KotlinScriptBasePluginsApplicator


class DefaultKotlinScriptBasePluginsApplicator : KotlinScriptBasePluginsApplicator {
    override fun apply(project: ProjectInternal) {
        project.plugins.apply(KotlinScriptBasePlugin::class.java)
    }
}


abstract class KotlinScriptBasePlugin : Plugin<Project> {
    override fun apply(project: Project): Unit = project.run {
        // Currently, there is no public API to do this in a project isolation safe way. Instead, do this in a non-safe way for now
        (project as ProjectInternal).owner.owner.projects.rootProject.mutableModel.plugins.apply(KotlinScriptRootPlugin::class.java)
        tasks.register("kotlinDslAccessorsReport", PrintAccessors::class.java)
    }
}


abstract class KotlinScriptRootPlugin : Plugin<Project> {
    override fun apply(project: Project): Unit =
        project.warnAboutDiscontinuedJsonProjectSchema()
}
