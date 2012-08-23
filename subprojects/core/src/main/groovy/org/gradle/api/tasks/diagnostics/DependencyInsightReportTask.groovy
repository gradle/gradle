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
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.diagnostics.internal.GraphRenderer
import org.gradle.api.tasks.diagnostics.internal.dependencies.RenderableDependency
import org.gradle.api.tasks.diagnostics.internal.insight.DependencyInsightReporter
import org.gradle.logging.StyledTextOutput
import org.gradle.logging.StyledTextOutputFactory

/**
 * by Szczepan Faber, created at: 8/17/12
 */
public class DependencyInsightReportTask extends DefaultTask {

    Configuration configuration;
    Closure includes;

    private StyledTextOutput output;
    private GraphRenderer renderer;

    public void setConfiguration(Configuration configuration) {
        this.configuration = configuration;
    }

    public void setIncludes(Closure includes) {
        this.includes = includes
    }

    @TaskAction
    public void interrogate() {
        output = getServices().get(StyledTextOutputFactory.class).create(getClass());
        renderer = new GraphRenderer(output);

        ResolutionResult result = configuration.getResolvedConfiguration().getResolutionResult();
        Set<? extends ResolvedDependencyResult> allDependencies = result.getAllDependencies()

        def selectedDependencies = allDependencies.findAll { ResolvedDependencyResult it ->
            //TODO SF this is quite crude for now but I need to get some feedback before implementing more.
            includes(it)
        }

        def sortedDeps = new DependencyInsightReporter().prepare(selectedDependencies)

        for (RenderableDependency dependency: sortedDeps) {
            renderer.visit(new Action<StyledTextOutput>() {
                public void execute(StyledTextOutput out) {
                    out.withStyle(StyledTextOutput.Style.Identifier).text(dependency.name);
                    if (dependency.description) {
                        out.withStyle(StyledTextOutput.Style.Description).text(dependency.description)
                    }
                }
            }, true);
            renderParents(dependency.getParents());
        }
    }

    private void renderParents(Set<? extends RenderableDependency> parents) {
        renderer.startChildren();
        int i = 0;
        for (RenderableDependency parent : parents) {
            boolean last = i++ == parents.size() - 1;
            render(parent, last);
        }
        renderer.completeChildren();
    }

    private void render(final RenderableDependency parent, boolean last) {
        def parents = parent.getParents();
        if (parents.size() == 0) {
            //root, don't print it.
            output.println();
            return;
        }
        renderer.visit(new Action<StyledTextOutput>() {
            public void execute(StyledTextOutput styledTextOutput) {
                styledTextOutput.text(parent.name);
            }
        }, last);
        renderParents(parents);
    }
}
