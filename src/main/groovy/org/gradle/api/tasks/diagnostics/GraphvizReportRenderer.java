/*
 * Copyright 2008 the original author or authors.
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
package org.gradle.api.tasks.diagnostics;

import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ResolvedConfiguration;
import org.gradle.api.artifacts.ResolvedDependency;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

/**
 * DependencyGrAaphRenderer that emits simple graphviz dot notation for a dependency tree.
 *
 * @author Phil Messenger
 */
public class GraphvizReportRenderer extends TextProjectReportRenderer implements DependencyReportRenderer {
    @Override
    public void startProject(Project project) {
        // Do nothing
    }

    public void startConfiguration(Configuration configuration) {
        // Do nothing
    }

    public void completeConfiguration(Configuration configuration) {
        // Do nothing
    }

    public void render(ResolvedConfiguration resolvedConfiguration) throws IOException {
        getFormatter().format("digraph %s{%n", "SomeConf");

        Set<String> edges = new HashSet<String>();

        for (ResolvedDependency resolvedDependency : resolvedConfiguration.getFirstLevelResolvedDependencies()) {
            buildDotDependencyTree(resolvedDependency, edges);
        }

        for (String edge : edges) {
            getFormatter().format("%s%n", edge);
        }

        getFormatter().format("}%n");
    }

    private void buildDotDependencyTree(ResolvedDependency root, Set<String> edges) {
        if (root.getAllModuleFiles().isEmpty()) {
            return;
        }
        for (ResolvedDependency dep : root.getChildren()) {
            String edge = "\"" + root.toString() + "\" -> \"" + dep.toString().replace('-', '_') + "\";";
            edges.add(edge);
        }

        for (ResolvedDependency dep : root.getChildren()) {
            buildDotDependencyTree(dep, edges);
        }
    }
}
