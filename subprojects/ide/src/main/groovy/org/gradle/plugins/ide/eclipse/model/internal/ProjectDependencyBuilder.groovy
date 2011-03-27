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

import org.gradle.plugins.ide.eclipse.model.ProjectDependency

/**
 * @author Szczepan Faber, @date: 11.03.11
 */
class ProjectDependencyBuilder {
    ProjectDependency build(gradleProject) {
        def name
        if (gradleProject.hasProperty('eclipseProject') && gradleProject.eclipseProject) {
            name = gradleProject.eclipseProject.projectName
        } else {
            //TODO SF: should we warn user? should we not add this dependency?
            name = gradleProject.name
        }
        new ProjectDependency('/' + name, true, null, [] as Set, gradleProject.path)
    }
}
