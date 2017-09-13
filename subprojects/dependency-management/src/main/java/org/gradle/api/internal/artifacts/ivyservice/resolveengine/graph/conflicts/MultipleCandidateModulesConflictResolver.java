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
package org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.conflicts;

import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.HasMultipleCandidateVersions;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.ComponentResolutionState;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.ConflictResolverDetails;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.ModuleConflictResolver;
import org.gradle.internal.component.model.ComponentResolveMetadata;

import java.util.LinkedList;
import java.util.List;

/**
 * This conflict resolver handles a special case, when multiple candidates of a module are
 * found, and that each of those candidates is itself selected within a list of possible answers.
 * The typical case are ranges, where we arbitrarily choose the latest version within range, at
 * the risk of selecting a version which doesn't match all constraints.
 *
 * This resolver will try to identify if the intersection of all candidates, and if it's not empty,
 * return the candidate which has the highest matching version as its upper bound.
 *
 * For example, if the 2 conflicting modules are [3,6] and [4,8], traditional conflict resolution
 * would return 8, because we resolved the first range to 6, the second range to 8, and we
 * select the highest of the two which is 8. This conflict resolver on the other hand will
 * realize that the two ranges are overlapping, and that it can select version 6.
 */
public class MultipleCandidateModulesConflictResolver implements ModuleConflictResolver {
    private final static MultipleCandidateModulesConflictResolver INSTANCE = new MultipleCandidateModulesConflictResolver();

    private MultipleCandidateModulesConflictResolver() {
    }

    public static MultipleCandidateModulesConflictResolver create() {
        return INSTANCE;
    }

    @Override
    public <T extends ComponentResolutionState> void select(ConflictResolverDetails<T> details) {
        List<String> intersection = null;
        for (T candidate : details.getCandidates()) {
            if (!candidate.isResolved()) {
                // this check avoids eagerly resolving metadata in case it can be deferred.
                // We can do this because this conflict resolver won't be of any use for cases where metadata wasn't necessary.
                return;
            }
            ComponentResolveMetadata md = candidate.getMetaData();
            if (!(md instanceof HasMultipleCandidateVersions)) {
                return;
            }
            // We make sure to use _lists_ in order to preserve order
            List<String> allVersions = ((HasMultipleCandidateVersions) md).getAllVersions();
            if (intersection == null) {
                intersection = new LinkedList<String>(allVersions);
            } else {
                intersection.retainAll(allVersions);
            }
            if (intersection.isEmpty()) {
                return;
            }
        }
        // intersection is ordered, so we need to take the first
        String version = intersection.get(0);
        for (T candidate : details.getCandidates()) {
            if (candidate.getId().getVersion().equals(version)) {
                details.select(candidate);
                return;
            }
        }
    }
}
