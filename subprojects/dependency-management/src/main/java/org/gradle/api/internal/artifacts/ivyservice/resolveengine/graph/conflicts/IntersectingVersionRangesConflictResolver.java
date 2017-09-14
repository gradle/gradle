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

import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.ComponentResolutionState;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.ConflictResolverDetails;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.ModuleConflictResolver;

/**
 * This conflict resolver handles intersecting ranges, by triggering a restart if there were new intersecting
 * ranges discovered during the resolution: for example, a node can be added with range [1,8]. Later, a different
 * node may be added with a different range: [3,6]. Traditional conflict resolution would choose between versions
 * 6 and 8. This conflict resolver will recognize that ranges [1,8] and [3,6] are intersecting, and will restart
 * resolution for the module. It's worth noting that the actual range intersection computation is not done by
 * the conflict resolver, but delegated to the {@link IntersectingVersionRangesHandler corresponding handler}.
 */
public class IntersectingVersionRangesConflictResolver implements ModuleConflictResolver {
    private final IntersectingVersionRangesHandler intersectingVersionRangesHandler;

    private int version = -1;

    public IntersectingVersionRangesConflictResolver(IntersectingVersionRangesHandler intersectingVersionRangesHandler) {
        this.intersectingVersionRangesHandler = intersectingVersionRangesHandler;
    }

    @Override
    public <T extends ComponentResolutionState> void select(ConflictResolverDetails<T> details) {
        for (T candidate : details.getCandidates()) {
            if (!candidate.isResolved()) {
                // this check avoids eagerly resolving metadata in case it can be deferred.
                // We can do this because this conflict resolver won't be of any use for cases where metadata wasn't necessary.
                return;
            }
            ModuleVersionIdentifier id = candidate.getId();
            String group = id.getGroup();
            String name = id.getName();
            if (!intersectingVersionRangesHandler.hasIntersectingRanges(group, name)) {
                return;
            }
        }
        int currentVersion = intersectingVersionRangesHandler.getVersion();
        if (currentVersion != version) {
            version = currentVersion;
            // only restart if they were new merges, otherwise we will create an infinite loop!
            details.restart();
        }
    }
}
