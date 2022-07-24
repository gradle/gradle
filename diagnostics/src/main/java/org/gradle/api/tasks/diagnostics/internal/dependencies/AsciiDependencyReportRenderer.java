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
package org.gradle.api.tasks.diagnostics.internal.dependencies;

import org.gradle.api.Action;
import org.gradle.api.NonNullApi;
import org.gradle.api.artifacts.result.ResolvedComponentResult;
import org.gradle.api.tasks.diagnostics.internal.ConfigurationDetails;
import org.gradle.api.tasks.diagnostics.internal.DependencyReportRenderer;
import org.gradle.api.tasks.diagnostics.internal.ProjectDetails;
import org.gradle.api.tasks.diagnostics.internal.TextReportRenderer;
import org.gradle.api.tasks.diagnostics.internal.graph.DependencyGraphsRenderer;
import org.gradle.api.tasks.diagnostics.internal.graph.NodeRenderer;
import org.gradle.api.tasks.diagnostics.internal.graph.SimpleNodeRenderer;
import org.gradle.api.tasks.diagnostics.internal.graph.nodes.RenderableDependency;
import org.gradle.api.tasks.diagnostics.internal.graph.nodes.RenderableModuleResult;
import org.gradle.initialization.StartParameterBuildOptions;
import org.gradle.internal.graph.GraphRenderer;
import org.gradle.internal.logging.text.StyledTextOutput;

import java.util.Collections;

import static org.gradle.internal.logging.text.StyledTextOutput.Style.Description;
import static org.gradle.internal.logging.text.StyledTextOutput.Style.Identifier;
import static org.gradle.internal.logging.text.StyledTextOutput.Style.Info;
import static org.gradle.internal.logging.text.StyledTextOutput.Style.UserInput;

/**
 * Simple dependency graph renderer that emits an ASCII tree.
 */
@NonNullApi
public class AsciiDependencyReportRenderer extends TextReportRenderer implements DependencyReportRenderer {
    private final ConfigurationDetailsAction configurationDetailsAction = new ConfigurationDetailsAction();
    private boolean hasConfigs;
    private GraphRenderer renderer;

    DependencyGraphsRenderer dependencyGraphRenderer;

    @Override
    public void startProject(ProjectDetails project) {
        super.startProject(project);
        prepareVisit();
    }

    void prepareVisit() {
        hasConfigs = false;
        renderer = new GraphRenderer(getTextOutput());
        dependencyGraphRenderer = new DependencyGraphsRenderer(getTextOutput(), renderer, NodeRenderer.NO_OP, new SimpleNodeRenderer());
    }

    @Override
    public void completeProject(ProjectDetails project) {
        if (!hasConfigs) {
            getTextOutput().withStyle(Info).println("No configurations");
        }
        super.completeProject(project);
    }

    @Override
    public void startConfiguration(ConfigurationDetails configuration) {
        if (hasConfigs) {
            getTextOutput().println();
        }
        hasConfigs = true;
        configurationDetailsAction.setConfiguration(configuration);
        renderer.visit(configurationDetailsAction, true);
    }

    @Override
    public void completeConfiguration(ConfigurationDetails configuration) {
    }

    @Override
    public void render(ConfigurationDetails configuration) {
        if (configuration.isCanBeResolved()) {
            ResolvedComponentResult result = configuration.getResolutionResultRoot().get();
            RenderableModuleResult root = new RenderableModuleResult(result);
            renderNow(root);
        } else {
            renderNow(configuration.getUnresolvableResult());
        }
    }

    void renderNow(RenderableDependency root) {
        if (root.getChildren().isEmpty()) {
            getTextOutput().withStyle(Info).text("No dependencies");
            getTextOutput().println();
            return;
        }

        dependencyGraphRenderer.render(Collections.singletonList(root));
    }

    @Override
    public void complete() {
        if (dependencyGraphRenderer != null) {
            dependencyGraphRenderer.complete();
        }

        getTextOutput().println();
        getTextOutput().text("A web-based, searchable dependency report is available by adding the ");
        getTextOutput().withStyle(UserInput).format("--%s", StartParameterBuildOptions.BuildScanOption.LONG_OPTION);
        getTextOutput().println(" option.");

        super.complete();
    }

    private class ConfigurationDetailsAction implements Action<StyledTextOutput> {

        private ConfigurationDetails configuration;

        public void setConfiguration(ConfigurationDetails configuration) {
            this.configuration = configuration;
        }

        @Override
        public void execute(StyledTextOutput styledTextOutput) {
            getTextOutput().withStyle(Identifier).text(configuration.getName());
            getTextOutput().withStyle(Description).text(getDescription(configuration));
            if (!configuration.isCanBeResolved()) {
                getTextOutput().withStyle(Info).text(" (n)");
            }
        }

        private String getDescription(ConfigurationDetails configuration) {
            String description = configuration.getDescription();
            if (description != null && !description.isEmpty()) {
                description = " - " + description;
            } else {
                description = "";
            }
            return description;
        }
    }
}
