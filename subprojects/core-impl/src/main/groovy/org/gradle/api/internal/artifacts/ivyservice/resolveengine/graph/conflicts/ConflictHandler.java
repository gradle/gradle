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

import org.gradle.api.Action;
import org.gradle.api.Nullable;
import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.ModuleConflictResolver;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.ModuleRevisionResolveState;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;

import java.util.LinkedHashMap;
import java.util.Map;

public class ConflictHandler { //TODO SF hide behind an interface

    private final static Logger LOGGER = Logging.getLogger(ConflictHandler.class);

    private final ModuleConflictResolver conflictResolver;
    private final Map<ModuleIdentifier, DefaultModuleConflict> conflicts = new LinkedHashMap<ModuleIdentifier, DefaultModuleConflict>();

    public ConflictHandler(ModuleConflictResolver conflictResolver) {
        this.conflictResolver = conflictResolver;
    }

    /**
     * Registers new module and returns an instance of a conflict if conflict exists.
     */
    @Nullable
    public ModuleConflict registerModule(CandidateModule module) {
        if (module.getVersions().size() > 1) {
            DefaultModuleConflict c = new DefaultModuleConflict(module.getVersions());
            conflicts.put(module.getId(), c);
            return c;
        }

        return null;
    }

    /**
     * Informs if there are any batched up conflicts.
     */
    public boolean hasConflicts() {
        return !conflicts.isEmpty();
    }

    /**
     * Resolves the conflict by delegating to the conflict resolver who selects single version from given candidates. Executes provided action against the conflict resolution result object.
     */
    public void resolveNextConflict(Action<ConflictResolutionResult> resolutionAction) {
        assert hasConflicts();
        ModuleIdentifier first = conflicts.keySet().iterator().next();
        DefaultModuleConflict firstConflict = conflicts.remove(first);
        ModuleRevisionResolveState selected = conflictResolver.select(firstConflict.getVersions());
        ConflictResolutionResult result = new DefaultConflictResolutionResult(firstConflict, selected);
        resolutionAction.execute(result);
        LOGGER.debug("Selected {} from conflicting modules {}.", selected, firstConflict.getVersions());
    }
}
