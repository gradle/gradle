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

import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.artifacts.result.ComponentSelectionCause;
import org.gradle.api.artifacts.result.ComponentSelectionDescriptor;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.GraphValidationException;
import org.gradle.internal.exceptions.ResolutionProvider;

import java.util.List;

public class VersionConflictException extends GraphValidationException implements ResolutionProvider {

    private final List<String> resolutions;

    public VersionConflictException(Conflict conflict, List<String> resolutions) {
        super(buildMessage(conflict));
        this.resolutions = resolutions;
    }

    private static String buildMessage(Conflict conflict) {
        String conflictDescription = getConflictDescription(conflict);
        ModuleIdentifier moduleId = conflict.getModuleId();

        return "Conflict found for module '" + moduleId.getGroup() + ":" + moduleId.getName() + "': " + conflictDescription;
    }

    private static String getConflictDescription(Conflict conflict) {
        String conflictDescription = null;
        for (ComponentSelectionDescriptor description : conflict.getSelectionReason().getDescriptions()) {
            if (description.getCause().equals(ComponentSelectionCause.CONFLICT_RESOLUTION)) {
                conflictDescription = description.getDescription();
            }
        }
        assert conflictDescription != null;
        return conflictDescription;
    }

    @Override
    public List<String> getResolutions() {
        return resolutions;
    }

}
