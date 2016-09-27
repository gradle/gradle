/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.resolveengine;

import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.internal.component.model.ComponentResolveMetadata;

import java.util.ArrayList;
import java.util.Collection;

class ProjectDependencyForcingResolver implements ModuleConflictResolver {
    private final ModuleConflictResolver delegate;

    ProjectDependencyForcingResolver(ModuleConflictResolver delegate) {
        this.delegate = delegate;
    }

    @Override
    public <T extends ComponentResolutionState> T select(Collection<? extends T> candidates) {
        // the collection will only be initialized if more than one project candidate is found
        Collection<T> projectCandidates = null;
        T foundProjectCandidate = null;
        // fine one or more project dependencies among conflicting modules
        for(T candidate: candidates) {
            ComponentResolveMetadata metaData = candidate.getMetaData();
            if (metaData!=null && metaData.getComponentId() instanceof ProjectComponentIdentifier) {
                if (foundProjectCandidate==null) {
                    // found the first project dependency
                    foundProjectCandidate = candidate;
                } else {
                    // found more than one
                    if (projectCandidates==null) {
                        projectCandidates = new ArrayList<T>();
                        projectCandidates.add(foundProjectCandidate);
                    }
                    projectCandidates.add(candidate);
                }
            }
        }
        // if more than one conflicting project dependencies
        // let the delegate resolver select among them
        if (projectCandidates!=null) {
            return delegate.select(projectCandidates);
        }
        // if found only one project dependency - return it, otherwise call the next resolver
        return foundProjectCandidate!=null ? foundProjectCandidate : delegate.select(candidates);
    }
}
