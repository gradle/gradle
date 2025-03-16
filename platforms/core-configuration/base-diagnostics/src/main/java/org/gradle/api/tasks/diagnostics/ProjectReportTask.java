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
import org.gradle.api.Incubating;
import org.gradle.api.Project;
import org.gradle.api.internal.project.ProjectHierarchyUtils;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.tasks.diagnostics.internal.ProjectDetails;
import org.gradle.api.tasks.diagnostics.internal.TextReportRenderer;
import org.gradle.initialization.BuildClientMetaData;
import org.gradle.internal.build.BuildStateRegistry;
import org.gradle.internal.build.IncludedBuildState;
import org.gradle.internal.graph.GraphRenderer;
import org.gradle.internal.logging.text.StyledTextOutput;
import org.gradle.plugin.software.internal.SoftwareTypeImplementation;
import org.gradle.plugin.software.internal.SoftwareTypeRegistry;
import org.gradle.util.Path;
import org.gradle.util.internal.CollectionUtils;
import org.gradle.work.DisableCachingByDefault;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.gradle.internal.logging.text.StyledTextOutput.Style.Description;
import static org.gradle.internal.logging.text.StyledTextOutput.Style.Header;
import static org.gradle.internal.logging.text.StyledTextOutput.Style.Identifier;
import static org.gradle.internal.logging.text.StyledTextOutput.Style.Info;
import static org.gradle.internal.logging.text.StyledTextOutput.Style.UserInput;

/**
 * <p>Displays a list of projects in the build. An instance of this type is used when you execute the {@code projects}
 * task from the command-line.</p>
 */
@DisableCachingByDefault(because = "Not worth caching")
public abstract class ProjectReportTask extends AbstractProjectBasedReportTask<ProjectReportTask.ProjectReportModel> {

    private final TextReportRenderer renderer = new TextReportRenderer();

    @Override
    protected TextReportRenderer getRenderer() {
        return renderer;
    }

    @Inject
    public BuildStateRegistry getBuildStateRegistry() {
        throw new UnsupportedOperationException();
    }

    @Inject
    protected abstract SoftwareTypeRegistry getSoftwareTypeRegistry();

    /**
     * Report model.
     *
     * @since 7.6
     */
    @Incubating
    public static final class ProjectReportModel {
        private final ProjectDetails project;
        private final List<ProjectReportModel> children;
        private final List<SoftwareTypeImplementation<?>> softwareTypes;
        private final boolean isRootProject;
        private final String tasksTaskPath;
        private final String rootProjectProjectsTaskPath;
        private final List<Path> includedBuildIdentityPaths;

        private ProjectReportModel(
            ProjectDetails project,
            List<ProjectReportModel> children,
            List<SoftwareTypeImplementation<?>> softwareTypes,
            boolean isRootProject,
            String tasksTaskPath,
            String rootProjectProjectsTaskPath,
            List<Path> includedBuildIdentityPaths
        ) {
            this.project = project;
            this.children = children;
            this.softwareTypes = softwareTypes;
            this.isRootProject = isRootProject;
            this.tasksTaskPath = tasksTaskPath;
            this.rootProjectProjectsTaskPath = rootProjectProjectsTaskPath;
            this.includedBuildIdentityPaths = includedBuildIdentityPaths;
        }

        /**
         * Investigates this project and all it's children to return the combined set
         * of all {@link SoftwareTypeImplementation}s registered by plugins used by them.
         */
        private Set<SoftwareTypeImplementation<?>> getAllSoftwareTypes() {
            Set<SoftwareTypeImplementation<?>> allSoftwareTypes = new HashSet<>(softwareTypes);
            children.forEach(p -> allSoftwareTypes.addAll(p.getAllSoftwareTypes()));
            return allSoftwareTypes;
        }
    }

    @Override
    protected ProjectReportModel calculateReportModelFor(Project project) {
        return new ProjectReportModel(
            ProjectDetails.of(project),
            calculateChildrenProjectsFor(project),
            getSoftwareTypesForProject(project),
            project == project.getRootProject(),
            project.absoluteProjectPath(ProjectInternal.TASKS_TASK),
            project.getRootProject().absoluteProjectPath(ProjectInternal.PROJECTS_TASK),
            calculateIncludedBuildIdentityPaths()
        );
    }

    private List<SoftwareTypeImplementation<?>> getSoftwareTypesForProject(Project project) {
        List<SoftwareTypeImplementation<?>> results = new ArrayList<>(1);
        getSoftwareTypeRegistry().getSoftwareTypeImplementations().values().forEach(registeredType -> {
            Class<?> softwareType = registeredType.getModelPublicType();
            if (project.getExtensions().findByType(softwareType) != null) {
                results.add(registeredType);
            }
        });
        return results;
    }

    private List<ProjectReportModel> calculateChildrenProjectsFor(Project project) {
        List<Project> childProjects = CollectionUtils.sort(ProjectHierarchyUtils.getChildProjectsForInternalUse(project));
        List<ProjectReportModel> children = new ArrayList<>(childProjects.size());
        for (Project childProject : childProjects) {
            children.add(calculateReportModelFor(childProject));
        }
        return children;
    }

    private List<Path> calculateIncludedBuildIdentityPaths() {
        Collection<? extends IncludedBuildState> includedBuilds = getBuildStateRegistry().getIncludedBuilds();
        List<Path> includedBuildIdentityPaths = new ArrayList<>(includedBuilds.size());
        for (IncludedBuildState includedBuild : includedBuilds) {
            includedBuildIdentityPaths.add(includedBuild.getIdentityPath());
        }
        return includedBuildIdentityPaths;
    }

    @Override
    protected void generateReportHeaderFor(Map<ProjectDetails, ProjectReportModel> modelsByProjectDetails) {
        renderSoftwareTypeInfo(modelsByProjectDetails);
        renderSectionTitle("Projects");
    }

    private void renderSectionTitle(String sectionName) {
        StyledTextOutput styledTextOutput = getRenderer().getTextOutput();
        styledTextOutput.println();
        styledTextOutput.withStyle(Header).append(sectionName).append(":");
        styledTextOutput.println();
    }

    @Override
    protected void generateReportFor(ProjectDetails project, ProjectReportModel model) {
        renderProjectTree(model);
        renderIncludedBuilds(model);
        renderHelp(model);
    }

    private void renderSoftwareTypeInfo(Map<ProjectDetails, ProjectReportModel> modelsByProjectDetails) {
        List<SoftwareTypeImplementation<?>> softwareTypes = modelsByProjectDetails.values().stream()
            .flatMap(model -> model.getAllSoftwareTypes().stream())
            .sorted(Comparator.comparing(SoftwareTypeImplementation::getSoftwareType))
            .collect(Collectors.toList());

        StyledTextOutput styledTextOutput = getRenderer().getTextOutput();
        if (!softwareTypes.isEmpty()) {
            renderSectionTitle("Available software types");
            styledTextOutput.println();

            softwareTypes.forEach(type -> {
                styledTextOutput.withStyle(Identifier).text(type.getSoftwareType());
                styledTextOutput.append(" (").append(type.getModelPublicType().getName()).append(")").println();
                styledTextOutput.append("        ").append("Defined in: ").append(type.getPluginClass().getName()).println();
                styledTextOutput.append("        ").append("Registered by: ").append(type.getRegisteringPluginClass().getName()).println();
            });
        }
    }

    private void renderProjectTree(ProjectReportModel model) {
        StyledTextOutput textOutput = getRenderer().getTextOutput();
        renderProject(model, new GraphRenderer(textOutput), true, textOutput);
        if (model.children.isEmpty()) {
            textOutput.withStyle(Info).text("No sub-projects");
            textOutput.println();
        }
    }

    private void renderProject(
        ProjectReportModel model,
        GraphRenderer renderer,
        boolean lastChild,
        StyledTextOutput textOutput
    ) {
        renderer.visit(styledTextOutput -> {
            styledTextOutput.text(StringUtils.capitalize(model.project.getDisplayName()));
            renderProjectType(model, textOutput);
            renderProjectDescription(model, textOutput);
        }, lastChild);
        renderer.startChildren();
        for (ProjectReportModel child : model.children) {
            renderProject(child, renderer, child == model.children.get(model.children.size() - 1), textOutput);
        }
        renderer.completeChildren();
    }

    private void renderProjectType(ProjectReportModel model, StyledTextOutput textOutput) {
        if (!model.softwareTypes.isEmpty()) {
            assert model.softwareTypes.size() == 1;
            textOutput.append(" (").append(model.softwareTypes.get(0).getSoftwareType()).append(")");
        }
    }

    private void renderProjectDescription(ProjectReportModel model, StyledTextOutput textOutput) {
        String projectDescription = model.project.getDescription();
        if (projectDescription != null && !projectDescription.isEmpty()) {
            String description = projectDescription.trim();
            int newlineInDescription = description.indexOf('\n');
            if (newlineInDescription > 0) {
                textOutput.withStyle(Description).text(" - " + description.substring(0, newlineInDescription) + "...");
            } else {
                textOutput.withStyle(Description).text(" - " + description);
            }
        }
    }

    private void renderIncludedBuilds(ProjectReportModel model) {
        StyledTextOutput textOutput = getRenderer().getTextOutput();
        if (model.isRootProject) {
            int index = 0;
            if (!model.includedBuildIdentityPaths.isEmpty()) {
                GraphRenderer renderer = new GraphRenderer(textOutput);
                renderSectionTitle("Included builds");
                textOutput.println();
                renderer.startChildren();
                for (Path includedBuildIdentityPath : model.includedBuildIdentityPaths) {
                    renderer.visit(
                        text -> textOutput.text("Included build '" + includedBuildIdentityPath + "'"),
                        (index + 1) == model.includedBuildIdentityPaths.size()
                    );
                    index++;
                }
                renderer.completeChildren();
            }
        }
    }

    private void renderHelp(ProjectReportModel model) {
        BuildClientMetaData metaData = getClientMetaData();
        StyledTextOutput textOutput = getRenderer().getTextOutput();

        textOutput.println();
        textOutput.text("To see a list of the tasks of a project, run ");
        metaData.describeCommand(
            textOutput.withStyle(UserInput),
            "<project-path>:" + ProjectInternal.TASKS_TASK
        );
        textOutput.println();

        textOutput.text("For example, try running ");
        ProjectReportModel exampleProject = model.children.isEmpty()
            ? model
            : model.children.get(0);
        metaData.describeCommand(
            textOutput.withStyle(UserInput),
            exampleProject.tasksTaskPath
        );
        textOutput.println();

        if (!model.isRootProject) {
            textOutput.println();
            textOutput.text("To see a list of all the projects in this build, run ");
            metaData.describeCommand(
                textOutput.withStyle(UserInput),
                model.rootProjectProjectsTaskPath
            );
            textOutput.println();
        }
    }
}
