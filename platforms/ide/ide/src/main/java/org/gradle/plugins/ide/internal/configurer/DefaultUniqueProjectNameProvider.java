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

import org.gradle.api.internal.project.ProjectIdentity;
import org.gradle.api.internal.project.ProjectState;
import org.gradle.api.internal.project.ProjectStateLookup;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

import static java.util.stream.Collectors.toList;

public class DefaultUniqueProjectNameProvider extends AbstractUniqueProjectNameProvider {

    @Nullable
    private Map<ProjectIdentity, String> deduplicated;

    /**
     * Creates a provider that produces unique project names based on project state information.
     *
     * @param projectStateLookup lookup used to obtain all project states for building and caching deduplicated names
     */
    public DefaultUniqueProjectNameProvider(ProjectStateLookup projectStateLookup) {
        super(projectStateLookup);
    }

    /**
     * Provide a deduplicated project name for the given project identity.
     *
     * @param projectIdentity the project whose unique name is requested
     * @return the deduplicated unique name for the project, or the project's original name if no deduplicated name is available
     */
    @Override
    public String getUniqueName(ProjectIdentity projectIdentity) {
        String uniqueName = getDeduplicatedNames().get(projectIdentity);
        return uniqueName != null ? uniqueName : projectIdentity.getProjectName();
    }

    /**
     * Lazily computes and caches a mapping from ProjectIdentity to its deduplicated (unique) project name.
     *
     * The mapping is computed on first invocation and stored for subsequent calls; this method is synchronized
     * to ensure thread-safe lazy initialization.
     *
     * @return a map that maps each ProjectIdentity to its deduplicated unique project name
     */
    private synchronized Map<ProjectIdentity, String> getDeduplicatedNames() {
        if (deduplicated == null) {
            HierarchicalElementDeduplicator<ProjectIdentity> deduplicator = new HierarchicalElementDeduplicator<>(new ProjectPathDeduplicationAdapter());
            List<ProjectIdentity> allProjects = projectStateLookup.getAllProjects().stream()
                .map(ProjectState::getIdentity)
                .collect(toList());
            this.deduplicated = deduplicator.deduplicate(allProjects);
        }
        return deduplicated;
    }

    private class ProjectPathDeduplicationAdapter implements HierarchicalElementAdapter<ProjectIdentity> {
        @Override
        public String getName(ProjectIdentity element) {
            return element.getProjectName();
        }

        @Override
        public String getIdentityName(ProjectIdentity element) {
            String identityName = element.getBuildTreePath().getName();
            return identityName != null ? identityName : element.getProjectName();
        }

        @Override
        @Nullable
        public ProjectIdentity getParent(ProjectIdentity element) {
            return findParentInBuildTree(element);
        }
    }
}
