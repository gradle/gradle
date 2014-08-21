/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.conflicts;

import org.gradle.api.internal.artifacts.ivyservice.resolveengine.ModuleConflictResolver;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.ModuleRevisionResolveState;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

class CompositeConflictResolver implements ModuleConflictResolver {

    private final List<ModuleConflictResolver> resolvers = new LinkedList<ModuleConflictResolver>();

    public <T extends ModuleRevisionResolveState> T select(Collection<? extends T> candidates) {
        for (ModuleConflictResolver r : resolvers) {
            T selection = r.select(candidates);
            if (selection != null) {
                return selection;
            }
        }
        throw new IllegalStateException(this.getClass().getSimpleName()
                + " was unable to select a candidate using resolvers: " + resolvers + ". Candidates: " + candidates);
    }

    void addFirst(ModuleConflictResolver conflictResolver) {
        resolvers.add(0, conflictResolver);
    }
}
