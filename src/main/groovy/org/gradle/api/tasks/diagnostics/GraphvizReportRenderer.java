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
import org.gradle.api.artifacts.report.IvyDependency;
import org.gradle.api.artifacts.report.IvyDependencyGraph;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

/**
 * DependencyGraphRenderer that emits simple graphviz dot notation for a dependency tree.
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

    public void render(IvyDependencyGraph graph) throws IOException {
        getFormatter().format("digraph %s{%n", graph.getConf());

        Set<String> edges = new HashSet<String>();

        buildDotDependencyTree(graph.getRoot(), edges);

        for (String edge : edges) {
            getFormatter().format("%s%n", edge);
        }

        getFormatter().format("}%n");
    }

    private void buildDotDependencyTree(IvyDependency root, Set<String> edges) {
        for (IvyDependency dep : root.getDependencies()) {
            String edge = "\"" + root.getName() + "\" -> \"" + dep.getName().replace('-', '_') + "\";";
            edges.add(edge);
        }

        for (IvyDependency dep : root.getDependencies()) {
            buildDotDependencyTree(dep, edges);
        }
    }
}
