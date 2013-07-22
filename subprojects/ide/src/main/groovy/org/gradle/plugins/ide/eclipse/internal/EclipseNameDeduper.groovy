/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.plugins.ide.eclipse.internal

import org.gradle.api.Project
import org.gradle.plugins.ide.eclipse.EclipsePlugin
import org.gradle.plugins.ide.internal.configurer.DeduplicationTarget
import org.gradle.plugins.ide.internal.configurer.ProjectDeduper

class EclipseNameDeduper {

    void configureRoot(Project rootProject) {
        def eclipseProjects = rootProject.allprojects.findAll { it.plugins.hasPlugin(EclipsePlugin) }
        new ProjectDeduper().dedupe(eclipseProjects, { project ->
            new DeduplicationTarget(project: project,
                    moduleName: project.eclipseProject.projectModel.name,
                    updateModuleName: { project.eclipseProject.projectModel.name = it })
        })
    }
}