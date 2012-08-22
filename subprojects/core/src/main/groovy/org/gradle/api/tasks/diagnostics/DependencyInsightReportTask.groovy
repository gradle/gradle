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


import org.gradle.api.Action
import org.gradle.api.DefaultTask
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.result.ResolutionResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.artifacts.result.ResolvedModuleVersionResult
import org.gradle.api.internal.artifacts.dependencygraph.ResolvedDependencyResultPrinter
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.diagnostics.internal.GraphRenderer
import org.gradle.api.tasks.diagnostics.internal.dependencies.DependencyComparator
import org.gradle.logging.StyledTextOutput
import org.gradle.logging.StyledTextOutputFactory

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

        ResolutionResult result = configuration.getResolvedConfiguration().getResolutionResult();
        Set<? extends ResolvedDependencyResult> allDependencies = result.getAllDependencies()

        def selectedDependencies = allDependencies.findAll { ResolvedDependencyResult it ->
            String dep = ResolvedDependencyResultPrinter.print(it);
            //TODO SF this is quite crude for now but I need to get some feedback before implementing more.
            dep.contains(dependency)
        }

        def sortedDeps = selectedDependencies.sort(new DependencyComparator());

        for (ResolvedDependencyResult dependency: sortedDeps) {
            renderer.visit(new Action<StyledTextOutput>() {
                public void execute(StyledTextOutput styledTextOutput) {
                    styledTextOutput.withStyle(StyledTextOutput.Style.Identifier).text(ResolvedDependencyResultPrinter.print(dependency));
                }
            }, true);
            renderDependees(dependency.getSelected().getDependees());
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
