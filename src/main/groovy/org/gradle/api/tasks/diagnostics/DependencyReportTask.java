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
import org.gradle.api.artifacts.report.IvyDependencyGraph;
import org.gradle.api.artifacts.report.IvyDependencyGraphBuilder;

import java.io.IOException;
import java.util.Comparator;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * The {@code DependencyReportTask} displays the dependency tree for a project. Can be configured to output to a file,
 * and to optionally output a graphviz compatible "dot" graph. This task is used when you execute the dependency list
 * command-line option.
 *
 * @author Phil Messenger
 */
public class DependencyReportTask extends AbstractReportTask {

    private DependencyReportRenderer renderer = new AsciiReportRenderer();

    public DependencyReportTask(Project project, String name) {
        super(project, name);
    }

    public DependencyReportRenderer getRenderer() {
        return renderer;
    }

    /**
     * Set the renderer to use to build a report. If unset, AsciiGraphRenderer will be used.
     */
    public void setRenderer(DependencyReportRenderer renderer) {
        this.renderer = renderer;
    }

    public void generate(Project project) throws IOException {
        SortedSet<Configuration> sortedConfigurations = new TreeSet<Configuration>(
                new Comparator<Configuration>() {
                    public int compare(Configuration conf1, Configuration conf2) {
                        return conf1.getName().compareTo(conf2.getName());
                    }
                });
        sortedConfigurations.addAll(project.getConfigurations().getAll());
        for (Configuration configuration : sortedConfigurations) {
            IvyDependencyGraphBuilder graphBuilder = new IvyDependencyGraphBuilder();

            // todo - move the following to Configuration, so that a IvyDependencyGraph can be obtained directly
            // todo - failOnResolve should lead to exception
            IvyDependencyGraph graph = graphBuilder.buildGraph(configuration.resolveAsReport(), configuration.getName());

            renderer.startConfiguration(configuration);
            renderer.render(graph);
            renderer.completeConfiguration(configuration);
        }
    }
}
