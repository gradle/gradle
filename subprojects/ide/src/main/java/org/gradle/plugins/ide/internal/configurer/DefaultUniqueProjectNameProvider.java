/*
 * Copyright 2017 the original author or authors.
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
package org.gradle.plugins.ide.internal.configurer;

import org.gradle.api.Project;
import org.gradle.api.internal.project.ProjectIdentifier;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.project.ProjectRegistry;

import java.util.Map;

public class DefaultUniqueProjectNameProvider implements UniqueProjectNameProvider {
    private final ProjectRegistry<ProjectIdentifier> projectRegistry;

    public DefaultUniqueProjectNameProvider(ProjectRegistry<ProjectIdentifier> projectRegistry) {
        this.projectRegistry = projectRegistry;
    }

    // TODO:DAZ Avoid duplicating the de-duplication work for every project.
    @Override
    public String getUniqueName(Project project) {
        HierarchicalElementDeduplicator<ProjectIdentifier> deduplicator = new HierarchicalElementDeduplicator<ProjectIdentifier>(new ProjectDeduplicationAdapter());
        Map<ProjectIdentifier, String> deduplicated = deduplicator.deduplicate(projectRegistry.getAllProjects());

        ProjectIdentifier projectIdentifier = (ProjectInternal) project;
        String newName = deduplicated.get(projectIdentifier);
        return newName == null ? project.getName() : newName;
    }

    // TODO:DAZ Simplify now that there are not multiple deduplicator types
    private static class ProjectDeduplicationAdapter implements NameDeduplicationAdapter<ProjectIdentifier> {
        @Override
        public String getName(ProjectIdentifier element) {
            return element.getName();
        }

        @Override
        public ProjectIdentifier getParent(ProjectIdentifier element) {
            return element.getParentIdentifier();
        }
    }
}
