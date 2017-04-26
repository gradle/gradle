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
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.initialization.ProjectPathRegistry;
import org.gradle.util.Path;

import java.util.Map;

public class DefaultUniqueProjectNameProvider implements UniqueProjectNameProvider {
    private final ProjectPathRegistry projectRegistry;
    private Map<Path, String> deduplicated;

    public DefaultUniqueProjectNameProvider(ProjectPathRegistry projectRegistry) {
        this.projectRegistry = projectRegistry;
    }

    @Override
    public String getUniqueName(Project project) {
        String uniqueName = getDeduplicatedNames().get(((ProjectInternal) project).getIdentityPath());
        if (uniqueName != null) {
            return uniqueName;
        }
        return project.getName();
    }

    private synchronized Map<Path, String> getDeduplicatedNames() {
        if (deduplicated == null) {
            HierarchicalElementDeduplicator<Path> deduplicator = new HierarchicalElementDeduplicator<Path>(new ProjectPathDeduplicationAdapter());
            this.deduplicated = deduplicator.deduplicate(projectRegistry.getAllProjectPaths());
        }
        return deduplicated;
    }

    private class ProjectPathDeduplicationAdapter implements HierarchicalElementAdapter<Path> {
        @Override
        public String getName(Path element) {
            if (element == Path.ROOT) {
                return projectRegistry.getProjectComponentIdentifier(Path.ROOT).getBuild().getName();
            }
            return element.getName();
        }

        @Override
        public Path getParent(Path element) {
            return element.getParent();
        }
    }
}
