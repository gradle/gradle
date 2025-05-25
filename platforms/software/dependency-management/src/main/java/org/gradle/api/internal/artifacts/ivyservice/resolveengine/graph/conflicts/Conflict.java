/*
 * Copyright 2023 the original author or authors.
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

import com.google.common.collect.ImmutableList;
import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.result.ComponentSelectionReason;

import java.util.List;

/**
 * Describes a version conflict between two or more versions of a module in a dependency graph.
 */
public class Conflict {

    private final List<Participant> participants;
    private final ModuleIdentifier moduleId;
    private final ComponentSelectionReason selectionReason;

    public Conflict(
        ImmutableList<Participant> participants,
        ModuleIdentifier moduleId,
        ComponentSelectionReason selectionReason
    ) {
        this.participants = participants;
        this.moduleId = moduleId;
        this.selectionReason = selectionReason;
    }

    public ComponentSelectionReason getSelectionReason() {
        return selectionReason;
    }

    public ModuleIdentifier getModuleId() {
        return moduleId;
    }

    public List<Participant> getParticipants() {
        return participants;
    }

    /**
     * A component that was involved in the conflict.
     */
    public static class Participant {

        private final String version;
        private final ComponentIdentifier id;

        public Participant(String version, ComponentIdentifier id) {
            this.version = version;
            this.id = id;
        }

        public String getVersion() {
            return version;
        }

        public ComponentIdentifier getId() {
            return id;
        }

    }

}
