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
import org.gradle.api.Project;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.tasks.diagnostics.internal.TextReportRenderer;
import org.gradle.initialization.BuildClientMetaData;
import org.gradle.internal.graph.GraphRenderer;
import org.gradle.logging.StyledTextOutput;
import org.gradle.util.CollectionUtils;
import org.gradle.util.GUtil;

import javax.inject.Inject;
import java.io.IOException;
import java.util.List;

import static org.gradle.logging.StyledTextOutput.Style.*;

/**
 * <p>Displays a list of projects in the build. An instance of this type is used when you execute the {@code projects}
 * task from the command-line.</p>
 */
public class ProjectReportTask extends AbstractReportTask {
    private TextReportRenderer renderer = new TextReportRenderer();

    @Override
    protected TextReportRenderer getRenderer() {
        return renderer;
    }

    @Inject
    protected BuildClientMetaData getClientMetaData() {
        throw new UnsupportedOperationException();
    }

    @Override
    protected void generate(Project project) throws IOException {
        BuildClientMetaData metaData = getClientMetaData();

        StyledTextOutput textOutput = getRenderer().getTextOutput();

        render(project, new GraphRenderer(textOutput), true, textOutput);
        if (project.getChildProjects().isEmpty()) {
            textOutput.withStyle(Info).text("No sub-projects");
            textOutput.println();
        }

        textOutput.println();
        textOutput.text("To see a list of the tasks of a project, run ");
        metaData.describeCommand(textOutput.withStyle(UserInput), String.format("<project-path>:%s",
                ProjectInternal.TASKS_TASK));
        textOutput.println();

        textOutput.text("For example, try running ");
        Project exampleProject = project.getChildProjects().isEmpty() ? project : getChildren(project).get(0);
        metaData.describeCommand(textOutput.withStyle(UserInput), exampleProject.absoluteProjectPath(
                ProjectInternal.TASKS_TASK));
        textOutput.println();

        if (project != project.getRootProject()) {
            textOutput.println();
            textOutput.text("To see a list of all the projects in this build, run ");
            metaData.describeCommand(textOutput.withStyle(UserInput), project.getRootProject().absoluteProjectPath(
                    ProjectInternal.PROJECTS_TASK));
            textOutput.println();
        }
    }

    private void render(final Project project, GraphRenderer renderer, boolean lastChild,
                        final StyledTextOutput textOutput) {
        renderer.visit(new Action<StyledTextOutput>() {
            public void execute(StyledTextOutput styledTextOutput) {
                styledTextOutput.text(StringUtils.capitalize(project.toString()));
                if (GUtil.isTrue(project.getDescription())) {
                    textOutput.withStyle(Description).format(" - %s", project.getDescription());
                }
            }
        }, lastChild);
        renderer.startChildren();
        List<Project> children = getChildren(project);
        for (Project child : children) {
            render(child, renderer, child == children.get(children.size() - 1), textOutput);
        }
        renderer.completeChildren();
    }

    private List<Project> getChildren(Project project) {
        return CollectionUtils.sort(project.getChildProjects().values());
    }
}
