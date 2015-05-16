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
import org.gradle.api.Incubating;
import org.gradle.api.Project;
import org.gradle.api.internal.tasks.options.Option;
import org.gradle.api.reporting.model.internal.*;
import org.gradle.api.tasks.TaskAction;
import org.gradle.logging.StyledTextOutput;
import org.gradle.logging.StyledTextOutputFactory;
import org.gradle.model.internal.core.ModelPath;
import org.gradle.model.internal.registry.ModelRegistry;

import javax.inject.Inject;

import static org.gradle.api.reporting.model.internal.ReportDetail.BARE;
import static org.gradle.api.reporting.model.internal.ReportFormat.TEXT;

/**
 * Displays some details about the configuration model of the project.
 */
@Incubating
public class ModelReport extends DefaultTask {

    @Option(description = "The level of detail to include on the model report")
    protected ReportDetail detail = ReportDetail.VERBOSE;

    @Option(description = "The format of the model report")
    protected ReportFormat format = TEXT;

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
        Project project = getProject();
        StyledTextOutput textOutput = getTextOutputFactory().create(ModelReport.class);
        ModelNodeDescriptor modelNodeDescriptor = getModelNodeDescriptor();
        ModelNodeRenderer renderer = new ModelNodeRenderer(modelNodeDescriptor);
        TextModelReportRenderer textModelReportRenderer = new TextModelReportRenderer(renderer);
        textModelReportRenderer.setOutput(textOutput);
        textModelReportRenderer.startProject(project);
        textModelReportRenderer.render(getModelRegistry().realizeNode(ModelPath.ROOT));
        textModelReportRenderer.completeProject(project);
        textModelReportRenderer.complete();
    }

    public ModelNodeDescriptor getModelNodeDescriptor() {
        ModelNodeDescriptor modelNodeDescriptor;
        if (detail == BARE) {
            modelNodeDescriptor = new BareStringNodeDescriptor();
        } else {
            modelNodeDescriptor = new BasicStringNodeDescriptor();
        }
        return modelNodeDescriptor;
    }

    public void setDetail(ReportDetail detail) {
        this.detail = detail;
    }

    public void setFormat(ReportFormat format) {
        this.format = format;
    }
}
