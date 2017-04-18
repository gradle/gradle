/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.plugins.ide.idea.internal;

import org.gradle.api.Project;
import org.gradle.api.Transformer;
import org.gradle.api.internal.project.ProjectIdentifier;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.plugins.ide.internal.configurer.HierarchicalElementDeduplicator;
import org.gradle.plugins.ide.internal.configurer.NameDeduplicationAdapter;
import org.gradle.util.CollectionUtils;

import java.util.Map;
import java.util.Set;

// TODO:DAZ Simplify now that there are not multiple deduplicator types
public class ProjectNameDeduplicator {

    public String getModuleName(Project project) {
        Set<ProjectIdentifier> ids = CollectionUtils.collect(project.getRootProject().getAllprojects(), new Transformer<ProjectIdentifier, Project>() {
            @Override
            public ProjectIdentifier transform(Project project) {
                return (ProjectInternal) project;
            }
        });

        HierarchicalElementDeduplicator<ProjectIdentifier> deduplicator = new HierarchicalElementDeduplicator<ProjectIdentifier>(new ProjectDeduplicationAdapter());
        Map<ProjectIdentifier, String> deduplicated = deduplicator.deduplicate(ids);

        ProjectInternal projectInternal = (ProjectInternal) project;
        String newName = deduplicated.get(projectInternal);
        return newName == null ? project.getName() : newName;
    }

    private static class ProjectDeduplicationAdapter implements NameDeduplicationAdapter<ProjectIdentifier> {
        @Override
        public String getName(ProjectIdentifier element) {
            String projectPath = element.getPath();
            if (projectPath.equals(":")) {
                return element.getName();
            }
            return projectPath.substring(projectPath.lastIndexOf(':') + 1);
        }

        @Override
        public ProjectIdentifier getParent(ProjectIdentifier element) {
            return element.getParentIdentifier();
        }
    }
}
