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
package org.gradle.plugins.ide.eclipse.model.internal

import org.gradle.api.Project
import org.gradle.plugins.ide.eclipse.EclipsePlugin
import org.gradle.plugins.ide.eclipse.model.ProjectDependency

class ProjectDependencyBuilder {
    ProjectDependency build(Project project, String declaredConfigurationName) {
        def name
        if (project.plugins.hasPlugin(EclipsePlugin)) {
            name = project.eclipse.project.name
        } else {
            name = project.name
        }
        def out = new ProjectDependency('/' + name, project.path)
        out.exported = true
        out.declaredConfigurationName = declaredConfigurationName
        out
    }
}
