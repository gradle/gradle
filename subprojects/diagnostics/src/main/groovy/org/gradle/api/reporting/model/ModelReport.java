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
import org.gradle.api.reporting.model.internal.ModelNodeRenderer;
import org.gradle.api.reporting.model.internal.TextModelReportRenderer;
import org.gradle.api.tasks.TaskAction;
import org.gradle.logging.StyledTextOutput;
import org.gradle.logging.StyledTextOutputFactory;
import org.gradle.model.internal.core.ModelPath;
import org.gradle.model.internal.registry.ModelRegistry;

import javax.inject.Inject;

/**
 * Displays some details about the configuration model of the project.
 */
@Incubating
public class ModelReport extends DefaultTask {

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
        ModelNodeRenderer renderer = new ModelNodeRenderer();

        TextModelReportRenderer textModelReportRenderer = new TextModelReportRenderer(renderer);

        textModelReportRenderer.setOutput(textOutput);
        textModelReportRenderer.startProject(project);

        textModelReportRenderer.render(getModelRegistry().realizeNode(ModelPath.ROOT));

        textModelReportRenderer.completeProject(project);
        textModelReportRenderer.complete();
    }
}
