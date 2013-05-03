/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.buildsetup.plugins.internal

import org.gradle.api.internal.DocumentationRegistry
import org.gradle.api.internal.artifacts.DependencyManagementServices
import org.gradle.api.internal.project.ProjectInternal

class ProjectLayoutSetupRegistryFactory {

    ProjectInternal project

    public ProjectLayoutSetupRegistryFactory(ProjectInternal project) {
        this.project = project;
    }

    ProjectLayoutSetupRegistry createProjectLayoutSetupRegistry() {
        DocumentationRegistry documentationRegistry = project.getServices().get(DocumentationRegistry)
        ProjectLayoutSetupRegistry registry = new ProjectLayoutSetupRegistry()

        // TODO maybe referencing the implementation class here is enough and instantiation
        // should be defererred when descriptor is requested.
        registry.add(new EmptyProjectSetupDescriptor(project, documentationRegistry));
        registry.add(new JavaLibraryProjectSetupDescriptor(project, documentationRegistry));
        registry.add(new PomProjectSetupDescriptor(project, project.getServices().get(DependencyManagementServices)))
        return registry
    }

}
