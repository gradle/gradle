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
import org.gradle.configuration.ImplicitTasksConfigurer;
import org.gradle.initialization.BuildClientMetaData;
import org.gradle.logging.StyledTextOutput;
import org.gradle.logging.StyledTextOutputFactory;
import org.gradle.util.GUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.gradle.logging.StyledTextOutput.Style.*;

/**
 * <p>Displays a list of projects in the build. It is used when you use the project list command-line option.</p>
 */
public class ProjectReportTask extends DefaultTask {
    private StyledTextOutput textOutput = getServices().get(StyledTextOutputFactory.class).create(ProjectReportTask.class);

    @TaskAction
    void listProjects() {
        BuildClientMetaData metaData = getServices().get(BuildClientMetaData.class);
        Project project = getProject();

        textOutput.println();
        render(project, new GraphRenderer(textOutput), true);
        if (project.getChildProjects().isEmpty()) {
            textOutput.withStyle(Info).text("No sub-projects");
            textOutput.println();
        }

        textOutput.println();
        textOutput.text("To see a list of the tasks of a project, run ");
        metaData.describeCommand(textOutput.withStyle(UserInput), String.format("<project-path>:%s", ImplicitTasksConfigurer.TASKS_TASK));
        textOutput.println();

        textOutput.text("For example, try running ");
        Project exampleProject = project.getChildProjects().isEmpty() ? project : getChildren(project).get(0);
        metaData.describeCommand(textOutput.withStyle(UserInput), exampleProject.absoluteProjectPath(ImplicitTasksConfigurer.TASKS_TASK));
        textOutput.println();

        if (project != project.getRootProject()) {
            textOutput.println();
            textOutput.text("To see a list of all the projects in this build, run ");
            metaData.describeCommand(textOutput.withStyle(UserInput), project.getRootProject().absoluteProjectPath(ImplicitTasksConfigurer.PROJECTS_TASK));
            textOutput.println();
        }
    }

    private void render(final Project project, GraphRenderer renderer, boolean lastChild) {
        renderer.visit(new Action<StyledTextOutput>() {
            public void execute(StyledTextOutput styledTextOutput) {
                styledTextOutput.text(StringUtils.capitalize(project.toString()));
                if (GUtil.isTrue(project.getDescription())) {
                    getTextOutput().withStyle(Description).format(" - %s", project.getDescription());
                }
            }
        }, lastChild);
        renderer.startChildren();
        List<Project> children = getChildren(project);
        for (Project child : children) {
            render(child, renderer, child == children.get(children.size() - 1));
        }
        renderer.completeChildren();
    }

    private List<Project> getChildren(Project project) {
        List<Project> children = new ArrayList<Project>(project.getChildProjects().values());
        Collections.sort(children);
        return children;
    }

    public StyledTextOutput getTextOutput() {
        return textOutput;
    }

    public void setTextOutput(StyledTextOutput textOutput) {
        this.textOutput = textOutput;
    }
}
