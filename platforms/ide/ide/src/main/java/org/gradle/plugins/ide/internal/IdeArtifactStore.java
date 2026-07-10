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

package org.gradle.plugins.ide.internal;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.internal.project.HoldsProjectState;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;

/**
 * This is separate from {@link DefaultIdeArtifactRegistry} so that the data can be shared across a build tree, while the {@link DefaultIdeArtifactRegistry} is scoped to a particular consuming project.
 */
@ServiceScope(Scope.BuildTree.class)
public class IdeArtifactStore implements HoldsProjectState {
    // This map is mutated concurrently while `IdeaPlugin` is applied to projects under IP. Synchronizing access to it
    // is a temporary solution. We should consider the problem more holistically.
    // See https://github.com/gradle/gradle/issues/38383
    private final ListMultimap<ProjectComponentIdentifier, IdeProjectMetadata> metadata = ArrayListMultimap.create();

    public synchronized void put(ProjectComponentIdentifier projectId, IdeProjectMetadata ideProjectMetadata) {
        metadata.put(projectId, ideProjectMetadata);
    }

    public synchronized Iterable<? extends IdeProjectMetadata> get(ProjectComponentIdentifier project) {
        return metadata.get(project);
    }

    @Override
    public synchronized void discardAll() {
        metadata.clear();
    }
}
