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

import com.google.common.collect.ImmutableList;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.GraphValidationException;
import org.gradle.internal.Pair;
import org.gradle.internal.exceptions.ResolutionProvider;
import org.gradle.internal.logging.text.TreeFormatter;

import java.util.Collection;
import java.util.List;

public class VersionConflictException extends GraphValidationException implements ResolutionProvider {
    private static final int MAX_SEEN_MODULE_COUNT = 10;
    private final List<Pair<List<? extends ModuleVersionIdentifier>, String>> conflicts;

    private final List<String> resolutions;

    private VersionConflictException(
        String message,
        List<Pair<List<? extends ModuleVersionIdentifier>, String>> conflicts,
        List<String> resolutions
    ) {
        super(message);
        this.conflicts = conflicts;
        this.resolutions = resolutions;
    }

    public List<Pair<List<? extends ModuleVersionIdentifier>, String>> getConflicts() {
        return conflicts;
    }

    private static String buildMessage(Collection<Pair<List<? extends ModuleVersionIdentifier>, String>> conflicts) {
        TreeFormatter formatter = new TreeFormatter();

        int size = conflicts.size();
        String plural = size > 1 ? "s" : "";
        formatter.node("Conflict" + plural + " found for the following module" + plural);
        formatter.startChildren();

        conflicts.stream().limit(MAX_SEEN_MODULE_COUNT)
            .forEach(allConflict -> formatter.node(allConflict.right));

        if (size > MAX_SEEN_MODULE_COUNT) {
            formatter.node("... and more");
        }
        formatter.endChildren();
        return formatter.toString();
    }

    private static String getDependencyNotation(Collection<Pair<List<? extends ModuleVersionIdentifier>, String>> conflicts) {
        return conflicts.stream()
            .findFirst()
            .map(p -> {
                ModuleVersionIdentifier identifier = p.left.get(0);
                return identifier.getGroup() + ":" + identifier.getName();
            })
            .orElse(null);
    }

    private static List<String> createResolutions(String projectPath, String configurationName, String dependencyNotation) {
        if (projectPath.equals(":")) {
            projectPath = "";
        }

        return ImmutableList.of("Run with: --scan or " + projectPath + ":dependencyInsight --configuration " +
            configurationName + " --dependency " + dependencyNotation + " to get more insight on how to solve the conflict.");
    }

    @Override
    public List<String> getResolutions() {
        return resolutions;
    }

    public static VersionConflictException create(
        String projectPath,
        String configurationName,
        Collection<Pair<List<? extends ModuleVersionIdentifier>, String>> conflicts
    ) {
        String message = buildMessage(conflicts);
        List<String> resolutions = createResolutions(projectPath, configurationName, getDependencyNotation(conflicts));
        return new VersionConflictException(message, ImmutableList.copyOf(conflicts), resolutions);
    }
}
