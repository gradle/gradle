/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.api.reporting.model;

import org.gradle.api.DefaultTask;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.reporting.model.internal.ModelNodeRenderer;
import org.gradle.api.reporting.model.internal.TextModelReportRenderer;
import org.gradle.api.tasks.Console;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.diagnostics.internal.ProjectDetails;
import org.gradle.api.tasks.options.Option;
import org.gradle.internal.logging.text.StyledTextOutput;
import org.gradle.internal.logging.text.StyledTextOutputFactory;
import org.gradle.model.internal.core.ModelNode;
import org.gradle.model.internal.core.ModelPath;
import org.gradle.model.internal.registry.ModelRegistry;
import org.gradle.work.DisableCachingByDefault;

import javax.inject.Inject;
import java.util.Locale;

/**
 * Displays some details about the configuration model of the project.
 * An instance of this type is used when you execute the {@code model} task from the command-line.
 */
@Deprecated
@DisableCachingByDefault(because = "Produces only non-cacheable console output")
public abstract class ModelReport extends DefaultTask {

    /**
     * The report format.
     * <ul>
     *     <li><i>full</i> (default value) will show details about types, rules and creators</li>
     *     <li><i>short</i> will only show nodes and their values</li>
     * </ul>
     */
    public enum Format {
        FULL,
        SHORT
    }

    private boolean showHidden;
    private Format format = Format.FULL;

    @Option(option = "showHidden", description = "Show hidden model elements.")
    public void setShowHidden(boolean showHidden) {
        this.showHidden = showHidden;
    }

    @Console
    public boolean isShowHidden() {
        return showHidden;
    }

    @Option(option = "format", description = "Output format (full, short)")
    public void setFormat(String format) {
        this.format = Format.valueOf(format.toUpperCase(Locale.ROOT));
    }

    @Console
    public Format getFormat() {
        return format;
    }

    @Inject
    protected StyledTextOutputFactory getTextOutputFactory() {
        throw new UnsupportedOperationException();
    }

    @Inject
    protected ModelRegistry getModelRegistry() {
        throw new UnsupportedOperationException();
    }

    @TaskAction
    public void report() {
        ProjectInternal project = (ProjectInternal) getProject();
        project.prepareForRuleBasedPlugins();

        StyledTextOutput textOutput = getTextOutputFactory().create(ModelReport.class);
        ModelNodeRenderer renderer = new ModelNodeRenderer(isShowHidden(), getFormat());

        TextModelReportRenderer textModelReportRenderer = new TextModelReportRenderer(renderer);

        textModelReportRenderer.setOutput(textOutput);
        ProjectDetails projectDetails = ProjectDetails.of(project);
        textModelReportRenderer.startProject(projectDetails);

        ModelRegistry modelRegistry = getModelRegistry();
        ModelNode rootNode = modelRegistry.realizeNode(ModelPath.ROOT);
        // Ensure binding validation has been done. This should happen elsewhere
        modelRegistry.bindAllReferences();
        textModelReportRenderer.render(rootNode);

        textModelReportRenderer.completeProject(projectDetails);
        textModelReportRenderer.complete();
    }
}
