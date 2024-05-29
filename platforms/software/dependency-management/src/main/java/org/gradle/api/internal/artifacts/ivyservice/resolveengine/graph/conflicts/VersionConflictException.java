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

import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.GraphValidationException;
import org.gradle.internal.exceptions.ResolutionProvider;
import org.gradle.internal.logging.text.TreeFormatter;

import java.util.Collection;
import java.util.List;

import static org.gradle.util.internal.TextUtil.getPluralEnding;

public class VersionConflictException extends GraphValidationException implements ResolutionProvider {
    private static final int MAX_SEEN_MODULE_COUNT = 10;
    private final Collection<Conflict> conflicts;

    private final List<String> resolutions;

    public VersionConflictException(Collection<Conflict> conflicts, List<String> resolutions) {
        super(buildMessage(conflicts));
        this.conflicts = conflicts;
        this.resolutions = resolutions;
    }

    public Collection<Conflict> getConflicts() {
        return conflicts;
    }

    private static String buildMessage(Collection<Conflict> conflicts) {
        TreeFormatter formatter = new TreeFormatter();

        String plural = getPluralEnding(conflicts);
        formatter.node("Conflict" + plural + " found for the following module" + plural);
        formatter.startChildren();

        conflicts.stream().limit(MAX_SEEN_MODULE_COUNT)
            .forEach(conflict -> formatter.node(conflict.getMessage()));

        if (conflicts.size() > MAX_SEEN_MODULE_COUNT) {
            formatter.node("... and more");
        }
        formatter.endChildren();
        return formatter.toString();
    }

    @Override
    public List<String> getResolutions() {
        return resolutions;
    }
}
