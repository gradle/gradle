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
import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.ComponentResolutionState;

import java.util.Set;

class PotentialConflictFactory {

    private static final PotentialConflict NO_CONFLICT = new NoConflict();

    static PotentialConflict potentialConflict(final ConflictContainer<ModuleIdentifier, ? extends ComponentResolutionState>.Conflict conflict) {
        if (conflict == null) {
            return NO_CONFLICT;
        }
        return new HasConflict(conflict.participants);
    }

    private static class HasConflict implements PotentialConflict {

        private final Set<ModuleIdentifier> participants;

        private HasConflict(Set<ModuleIdentifier> participants) {
            this.participants = participants;
        }

        @Override
        public void withParticipatingModules(Action<ModuleIdentifier> action) {
            for (ModuleIdentifier participant : participants) {
                action.execute(participant);
            }
        }

        @Override
        public boolean conflictExists() {
            return true;
        }
    }

    private static class NoConflict implements PotentialConflict {
        @Override
        public void withParticipatingModules(Action<ModuleIdentifier> action) {
        }

        @Override
        public boolean conflictExists() {
            return false;
        }
    }
}
