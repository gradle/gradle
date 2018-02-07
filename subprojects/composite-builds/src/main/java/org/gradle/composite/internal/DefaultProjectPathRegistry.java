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
package org.gradle.composite.internal;

import com.google.common.collect.Maps;
import org.gradle.api.Transformer;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.specs.Spec;
import org.gradle.initialization.ProjectPathRegistry;
import org.gradle.util.CollectionUtils;
import org.gradle.util.Path;

import java.util.Map;
import java.util.Set;

public class DefaultProjectPathRegistry implements ProjectPathRegistry {
    // TODO: Synchronization
    private final Map<Path, ProjectPathEntry> allProjects = Maps.newLinkedHashMap();

    void add(Path projectIdentityPath, ProjectComponentIdentifier identifier, boolean isImplicitBuild) {
        allProjects.put(projectIdentityPath, new ProjectPathEntry(identifier, isImplicitBuild));
    }

    @Override
    public Set<Path> getAllProjectPaths() {
        return allProjects.keySet();
    }

    @Override
    public Set<Path> getAllExplicitProjectPaths() {
        return filterProjectPaths(false);
    }

    @Override
    public Set<Path> getAllImplicitProjectPaths() {
        return filterProjectPaths(true);
    }

    private Set<Path> filterProjectPaths(final boolean isAddedImplicitly) {
        return CollectionUtils.collect(
            CollectionUtils.filter(allProjects.entrySet(), new Spec<Map.Entry<Path, ProjectPathEntry>>() {
                @Override
                public boolean isSatisfiedBy(Map.Entry<Path, ProjectPathEntry> entry) {
                    return isAddedImplicitly == entry.getValue().isAddedImplicitly();
                }
            }),
            new Transformer<Path, Map.Entry<Path, ProjectPathEntry>>() {
                @Override
                public Path transform(Map.Entry<Path, ProjectPathEntry> entry) {
                    return entry.getKey();
                }
            });
    }

    @Override
    public ProjectComponentIdentifier getProjectComponentIdentifier(Path identityPath) {
        return allProjects.get(identityPath).getIdentifier();
    }

    private static class ProjectPathEntry {
        private final ProjectComponentIdentifier identifier;
        private final boolean isAddedImplicitly;

        public ProjectPathEntry(ProjectComponentIdentifier identifier, boolean isAddedImplicitly) {
            this.identifier = identifier;
            this.isAddedImplicitly = isAddedImplicitly;
        }

        public ProjectComponentIdentifier getIdentifier() {
            return identifier;
        }

        public boolean isAddedImplicitly() {
            return isAddedImplicitly;
        }
    }
}
