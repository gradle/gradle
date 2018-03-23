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
package org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.conflicts;

import org.gradle.api.internal.artifacts.ivyservice.resolveengine.ComponentResolutionState;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.RejectedComponentMessageBuilder;

import java.util.Collection;

public class RejectCheckingConflictResolverDetails<T extends ComponentResolutionState> extends DefaultConflictResolverDetails<T> {
    public RejectCheckingConflictResolverDetails(Collection<? extends T> participants) {
        super(participants);
    }

    @Override
    public void select(T selected) {
        // TODO:DAZ Should probably not be verifying the version for a resolve project component
        String version = selected.getVersion();

        // TODO:DAZ Should be setting this earlier, not during conflict resolution
        // Currently, the message-building algorithm depends on the post-conflict-resolve state
        for (T candidate : getCandidates()) {
            if (candidate.getVersionConstraint() != null && candidate.getVersionConstraint().getRejectedSelector() != null && candidate.getVersionConstraint().getRejectedSelector().accept(version)) {
                String message = new RejectedComponentMessageBuilder().buildFailureMessage(getCandidates());
                selected.reject(message);
                break;
            }
        }

        super.select(selected);
    }
}
