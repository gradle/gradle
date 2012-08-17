/*
 * Copyright 2012 the original author or authors.
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

import org.gradle.api.Action;
import org.gradle.api.DefaultTask;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.internal.artifacts.dependencygraph.ResolvedDependencyResultPrinter;
import org.gradle.api.internal.dependencygraph.api.DependencyGraph;
import org.gradle.api.internal.dependencygraph.api.ResolvedDependencyResult;
import org.gradle.api.internal.dependencygraph.api.ResolvedModuleVersionResult;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.diagnostics.internal.GraphRenderer;
import org.gradle.logging.StyledTextOutput;
import org.gradle.logging.StyledTextOutputFactory;

import java.util.Set;

/**
 * by Szczepan Faber, created at: 8/17/12
 */
public class DependencyInsightReportTask extends DefaultTask {

    Configuration configuration;
    String dependency;

    private StyledTextOutput output;
    private GraphRenderer renderer;

    public void setConfiguration(Configuration configuration) {
        this.configuration = configuration;
    }

    public void setDependency(String dependency) {
        this.dependency = dependency;
    }

    @TaskAction
    public void interrogate() {
        output = getServices().get(StyledTextOutputFactory.class).create(getClass());
        renderer = new GraphRenderer(output);

        DependencyGraph dependencyGraph = configuration.getResolvedConfiguration().getDependencyGraph();
        Set<? extends ResolvedDependencyResult> allDependencies = dependencyGraph.getAllDependencies();

        for (final ResolvedDependencyResult dependencyResult : allDependencies) {
            String requested = ResolvedDependencyResultPrinter.print(dependencyResult);
            if (requested.contains(dependency)) {
                renderer.visit(new Action<StyledTextOutput>() {
                    public void execute(StyledTextOutput styledTextOutput) {
                        styledTextOutput.withStyle(StyledTextOutput.Style.Identifier).text(dependencyResult);
                    }
                }, true);
                renderDependees(dependencyResult.getSelected().getDependees());
            }
        }
    }

    private void renderDependees(Set<? extends ResolvedModuleVersionResult> dependees) {
        renderer.startChildren();
        int i = 0;
        for (ResolvedModuleVersionResult dependee : dependees) {
            boolean last = i++ == dependees.size() - 1;
            render(dependee, last);
        }
        renderer.completeChildren();
    }

    private void render(final ResolvedModuleVersionResult dependee, boolean last) {
        Set<? extends ResolvedModuleVersionResult> dependees = dependee.getDependees();
        if (dependees.size() == 0) {
            //root, don't print it.
            output.println();
            return;
        }
        renderer.visit(new Action<StyledTextOutput>() {
            public void execute(StyledTextOutput styledTextOutput) {
                styledTextOutput.text(dependee);
            }
        }, last);
        renderDependees(dependees);
    }
}
