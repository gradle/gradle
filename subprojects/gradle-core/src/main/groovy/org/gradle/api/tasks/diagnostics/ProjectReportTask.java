/*
 * Copyright 2010 the original author or authors.
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

import org.apache.commons.lang.StringUtils;
import org.gradle.api.Action;
import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.diagnostics.internal.GraphRenderer;
import org.gradle.api.tasks.diagnostics.internal.TextReportRenderer;
import org.gradle.logging.StyledTextOutput;
import org.gradle.logging.StyledTextOutputFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * <p>Displays a list of projects in the build. It is used when you use the project list command-line option.</p>
 */
public class ProjectReportTask extends DefaultTask {
    private StyledTextOutput textOutput = getServices().get(StyledTextOutputFactory.class).create(ProjectReportTask.class);

    @TaskAction
    void listProjects() {
        TextReportRenderer renderer = new TextReportRenderer();
        renderer.setOutput(textOutput);
        renderer.writeHeading(StringUtils.capitalize(getProject().getGradle().toString()));
        render(getProject().getRootProject(), new GraphRenderer(textOutput), true);
    }

    private void render(final Project project, GraphRenderer renderer, boolean lastChild) {
        renderer.visit(new Action<StyledTextOutput>() {
            public void execute(StyledTextOutput styledTextOutput) {
                styledTextOutput.text(StringUtils.capitalize(project.toString()));
            }
        }, lastChild);
        renderer.startChildren();
        List<Project> children = new ArrayList<Project>(project.getChildProjects().values());
        Collections.sort(children);
        for (Project child : children) {
            render(child, renderer, child == children.get(children.size() - 1));
        }
        renderer.completeChildren();
    }

    public StyledTextOutput getTextOutput() {
        return textOutput;
    }

    public void setTextOutput(StyledTextOutput textOutput) {
        this.textOutput = textOutput;
    }
}
