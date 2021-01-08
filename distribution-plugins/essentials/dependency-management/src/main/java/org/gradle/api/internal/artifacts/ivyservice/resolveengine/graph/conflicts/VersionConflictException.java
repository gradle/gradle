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
import org.gradle.internal.logging.text.TreeFormatter;

import java.util.Collection;
import java.util.List;

public class VersionConflictException extends GraphValidationException {
    private static final int MAX_SEEN_MODULE_COUNT = 10;
    private final List<Pair<List<? extends ModuleVersionIdentifier>, String>> conflicts;

    public VersionConflictException(String projectPath, String configurationName, Collection<Pair<List<? extends ModuleVersionIdentifier>, String>> conflicts) {
        super(buildMessage(projectPath, configurationName, conflicts));
        this.conflicts = ImmutableList.copyOf(conflicts);
    }

    public List<Pair<List<? extends ModuleVersionIdentifier>, String>> getConflicts() {
        return conflicts;
    }

    private static String buildMessage(String projectPath, String configurationName, Collection<Pair<List<? extends ModuleVersionIdentifier>, String>> conflicts) {
        TreeFormatter formatter = new TreeFormatter();
        String dependencyNotation = null;
        int count = 0;
        formatter.node("Conflict(s) found for the following module(s)");
        formatter.startChildren();
        for (Pair<List<? extends ModuleVersionIdentifier>, String> allConflict : conflicts) {
            if (count > MAX_SEEN_MODULE_COUNT) {
                formatter.node("... and more");
                break;
            }
            formatter.node(allConflict.right);
            count++;
            if (dependencyNotation == null) {
                ModuleVersionIdentifier identifier = allConflict.getLeft().get(0);
                dependencyNotation = identifier.getGroup() + ":" + identifier.getName();
            }
        }
        formatter.endChildren();
        appendInsight(projectPath, configurationName, formatter, dependencyNotation);
        return formatter.toString();
    }

    private static void appendInsight(String projectPath, String configurationName, TreeFormatter formatter, String dependencyNotation) {
        if (projectPath.equals(":")) {
            projectPath = "";
        }
        formatter.node("Run with:");
        formatter.node("    --scan or");
        formatter.node("    " + projectPath + ":dependencyInsight --configuration " + configurationName + " --dependency " + dependencyNotation);
        formatter.node("to get more insight on how to solve the conflict.");
    }
}
