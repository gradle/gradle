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
package org.gradle.api.tasks.diagnostics.internal;

import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.ResolvedConfiguration;
import org.gradle.api.artifacts.result.ResolutionResult;
import org.gradle.api.tasks.diagnostics.internal.dependencies.RenderableDependency;
import org.gradle.api.tasks.diagnostics.internal.dependencies.RenderableModuleResult;
import org.gradle.logging.StyledTextOutput;
import org.gradle.util.GUtil;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.gradle.logging.StyledTextOutput.Style.*;

/**
 * Simple dependency graph renderer that emits an ASCII tree.
 *
 * @author Phil Messenger
 */
public class    AsciiReportRenderer extends TextReportRenderer implements DependencyReportRenderer {
    private boolean hasConfigs;
    private boolean hasCyclicDependencies;
    private GraphRenderer renderer;

    @Override
    public void startProject(Project project) {
        super.startProject(project);
        hasConfigs = false;
        hasCyclicDependencies = false;
    }

    @Override
    public void completeProject(Project project) {
        if (!hasConfigs) {
            getTextOutput().withStyle(Info).println("No configurations");
        }
        super.completeProject(project);
    }

    public void startConfiguration(final Configuration configuration) {
        if (hasConfigs) {
            getTextOutput().println();
        }
        hasConfigs = true;
        renderer = new GraphRenderer(getTextOutput());
        renderer.visit(new Action<StyledTextOutput>() {
            public void execute(StyledTextOutput styledTextOutput) {
                getTextOutput().withStyle(Identifier).text(configuration.getName());
                getTextOutput().withStyle(Description).text(getDescription(configuration));
            }
        }, true);
    }

    private String getDescription(Configuration configuration) {
        return GUtil.isTrue(configuration.getDescription()) ? " - " + configuration.getDescription() : "";
    }

    public void completeConfiguration(Configuration configuration) {}

    public void render(Configuration configuration) throws IOException {
        ResolvedConfiguration resolvedConfiguration = configuration.getResolvedConfiguration();
        ResolutionResult result = configuration.getIncoming().getResolutionResult();
        RenderableDependency root = new RenderableModuleResult(result.getRoot());

        renderNow(root);

        resolvedConfiguration.rethrowFailure();
    }

    void renderNow(RenderableDependency root) {
        if (root.getChildren().isEmpty()) {
            getTextOutput().withStyle(Info).text("No dependencies");
            getTextOutput().println();
            return;
        }

        renderChildren(root.getChildren(), new HashSet<ModuleVersionIdentifier>());
    }

    public void complete() throws IOException {
        if (hasCyclicDependencies) {
            getTextOutput().withStyle(Info).println("\n(*) - dependencies omitted (listed previously)");
        }
        
        super.complete();
    }
    
    private void render(final RenderableDependency resolvedDependency, Set<ModuleVersionIdentifier> visitedDependencyNames, boolean lastChild) {
        final boolean isFirstVisitOfDependencyInConfiguration = visitedDependencyNames.add(resolvedDependency.getId());
        if (!isFirstVisitOfDependencyInConfiguration) {
            hasCyclicDependencies = true;
        }

        renderer.visit(new Action<StyledTextOutput>() {
            public void execute(StyledTextOutput styledTextOutput) {
                getTextOutput().text(resolvedDependency.getName());
                StyledTextOutput infoStyle = getTextOutput().withStyle(Info);

                if (!isFirstVisitOfDependencyInConfiguration) {
                    infoStyle.append(" (*)");
                }
            }
        }, lastChild);

        if (isFirstVisitOfDependencyInConfiguration) {
            renderChildren(resolvedDependency.getChildren(), visitedDependencyNames);
        }
    }

    private void renderChildren(Set<RenderableDependency> children, Set<ModuleVersionIdentifier> visitedDependencyNames) {
        renderer.startChildren();
        List<RenderableDependency> mergedChildren = new ArrayList<RenderableDependency>(children);
        for (int i = 0; i < mergedChildren.size(); i++) {
            RenderableDependency dependency = mergedChildren.get(i);
            render(dependency, visitedDependencyNames, i == mergedChildren.size() - 1);
        }
        renderer.completeChildren();
    }

}
