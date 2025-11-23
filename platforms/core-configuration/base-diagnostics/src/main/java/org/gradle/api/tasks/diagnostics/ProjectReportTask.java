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

import org.apache.commons.lang3.StringUtils;
import org.gradle.api.Incubating;
import org.gradle.api.Project;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.project.ProjectOrderingUtil;
import org.gradle.api.tasks.diagnostics.internal.ProjectDetails;
import org.gradle.api.tasks.diagnostics.internal.TextReportRenderer;
import org.gradle.initialization.BuildClientMetaData;
import org.gradle.internal.build.BuildStateRegistry;
import org.gradle.internal.build.IncludedBuildState;
import org.gradle.internal.graph.GraphRenderer;
import org.gradle.internal.logging.text.StyledTextOutput;
import org.gradle.plugin.software.internal.ProjectFeatureImplementation;
import org.gradle.util.Path;
import org.gradle.work.DisableCachingByDefault;

import javax.inject.Inject;
import java.io.File;
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
    public abstract BuildStateRegistry getBuildStateRegistry();

    @Inject
    @SuppressWarnings("deprecation")
    protected abstract org.gradle.plugin.software.internal.SoftwareTypeRegistry getSoftwareTypeRegistry();

    /**
     * Report model.
     *
     * @since 7.6
     */
    @Incubating
    public static final class ProjectReportModel {
        private final ProjectDetails project;
        private final List<ProjectReportModel> children;
        private final List<ProjectFeatureImplementation<?, ?>> softwareTypes;
        private final boolean isRootProject;
        private final String tasksTaskPath;
        private final String rootProjectProjectsTaskPath;
        private final List<Path> includedBuildIdentityPaths;

        private ProjectReportModel(
            ProjectDetails project,
            List<ProjectReportModel> children,
            List<ProjectFeatureImplementation<?, ?>> softwareTypes,
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
         * of all {@link ProjectFeatureImplementation}s registered by plugins used by them.
         */
        private Set<ProjectFeatureImplementation<?, ?>> getAllSoftwareTypes() {
            Set<ProjectFeatureImplementation<?, ?>> allSoftwareTypes = new HashSet<>(softwareTypes);
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
            project.getParent() == null,
            project.absoluteProjectPath(ProjectInternal.TASKS_TASK),
            project.getRootProject().absoluteProjectPath(ProjectInternal.PROJECTS_TASK),
            calculateIncludedBuildIdentityPaths()
        );
    }

    private List<ProjectFeatureImplementation<?, ?>> getSoftwareTypesForProject(Project project) {
        List<ProjectFeatureImplementation<?, ?>> results = new ArrayList<>(1);
        getSoftwareTypeRegistry().getProjectFeatureImplementations().values().forEach(registeredType -> {
            Class<?> softwareType = registeredType.getDefinitionPublicType();
            if (project.getExtensions().findByType(softwareType) != null) {
                results.add(registeredType);
            }
        });
        return results;
    }

    private List<ProjectReportModel> calculateChildrenProjectsFor(Project project) {
        return ((ProjectInternal) project).getOwner().getChildProjects().stream()
            .sorted(ProjectOrderingUtil::compare)
            .map(state -> calculateReportModelFor(state.getMutableModel()))
            .collect(Collectors.toList());
    }

    private List<Path> calculateIncludedBuildIdentityPaths() {
        Collection<? extends IncludedBuildState> includedBuilds = getBuildStateRegistry().getIncludedBuilds();
        List<Path> includedBuildIdentityPaths = new ArrayList<>(includedBuilds.size());
        for (IncludedBuildState includedBuild : includedBuilds) {
            includedBuildIdentityPaths.add(includedBuild.getIdentityPath());
        }
        return includedBuildIdentityPaths;
    }

    private void renderSectionTitle(String sectionName, StyledTextOutput textOutput) {
        textOutput.println();
        textOutput.withStyle(Header).append(sectionName).append(":");
        textOutput.println();
    }

    @Override
    protected void generateReportHeaderFor(Map<ProjectDetails, ProjectReportModel> modelsByProjectDetails) {
        renderSoftwareTypeInfo(modelsByProjectDetails);
        renderSectionTitle("Projects", getRenderer().getTextOutput());
    }

    private void renderSoftwareTypeInfo(Map<ProjectDetails, ProjectReportModel> modelsByProjectDetails) {
        List<ProjectFeatureImplementation<?, ?>> softwareTypes = modelsByProjectDetails.values().stream()
            .flatMap(model -> model.getAllSoftwareTypes().stream())
            .sorted(Comparator.comparing(ProjectFeatureImplementation::getFeatureName))
            .collect(Collectors.toList());

        StyledTextOutput textOutput = getRenderer().getTextOutput();
        if (!softwareTypes.isEmpty()) {
            renderSectionTitle("Available project types", textOutput);
            textOutput.println();

            softwareTypes.forEach(type -> {
                textOutput.withStyle(Identifier).text(type.getFeatureName());
                textOutput.append(" (").append(type.getDefinitionPublicType().getName()).append(")").println();
                textOutput.append("        ").append("Defined in: ").append(type.getPluginClass().getName()).println();
                textOutput.append("        ").append("Registered by: ").append(type.getRegisteringPluginClass().getName()).println();
            });
        }
    }

    @Override
    protected void generateReportFor(ProjectDetails project, ProjectReportModel model) {
        StyledTextOutput textOutput = getRenderer().getTextOutput();
        renderRootProjectLocation(model, textOutput);
        renderRootProjectDescription(model, textOutput);

        renderProjectHierarchy(model, textOutput);
        renderProjectsLocations(model, textOutput);
        renderIncludedBuilds(model, textOutput);
        renderHelp(model, textOutput);
    }

    private void renderRootProjectLocation(ProjectReportModel model, StyledTextOutput textOutput) {
        String projectDir = model.project.getAbsoluteProjectDir();
        textOutput.withStyle(Info).append("Location: ");
        textOutput.withStyle(Description).append(projectDir);
        textOutput.println();
    }

    private void renderRootProjectDescription(ProjectReportModel model, StyledTextOutput textOutput) {
        String description = model.project.getDescription();
        if (description != null && !description.isEmpty()) {
            description = description.trim();
            textOutput.withStyle(Info).append("Description: ");
            textOutput.withStyle(Description).append(description);
            textOutput.println();
        }
    }

    private void renderProjectHierarchy(ProjectReportModel model, StyledTextOutput textOutput) {
        renderSectionTitle("Project hierarchy", textOutput);
        textOutput.println();

        renderProject(model, new GraphRenderer(textOutput), true);
        if (model.children.isEmpty()) {
            textOutput.withStyle(Info).text("No sub-projects");
            textOutput.println();
        }
    }

    private void renderProject(
        ProjectReportModel model,
        GraphRenderer renderer,
        boolean lastChild
    ) {
        renderer.visit(textOutput -> {
            textOutput.text(StringUtils.capitalize(model.project.getDisplayName()));
            renderProjectType(model);
            if (!model.isRootProject) {
                renderProjectDescription(model, textOutput);
            }
        }, lastChild);
        renderer.startChildren();
        for (ProjectReportModel child : model.children) {
            renderProject(child, renderer, child == model.children.get(model.children.size() - 1));
        }
        renderer.completeChildren();
    }

    private void renderProjectDescription(ProjectReportModel model, StyledTextOutput textOutput) {
        String description = model.project.getDescription();
        if (description != null && !description.isEmpty()) {
            description = description.trim();
            int newlineInDescription = description.indexOf('\n');
            if (newlineInDescription > 0) {
                textOutput.withStyle(Description).text(" - " + description.substring(0, newlineInDescription) + "...");
            } else {
                textOutput.withStyle(Description).text(" - " + description);
            }
        }
    }

    private void renderProjectType(ProjectReportModel model) {
        if (!model.softwareTypes.isEmpty()) {
            StyledTextOutput textOutput = getRenderer().getTextOutput();
            assert model.softwareTypes.size() == 1;
            textOutput.append(" (").append(model.softwareTypes.get(0).getFeatureName()).append(")");
        }
    }

    private void renderProjectsLocations(ProjectReportModel model, StyledTextOutput textOutput) {
        List<ProjectDetails> projectLocations = new ArrayList<>(model.children.size());
        gatherLocations(model, projectLocations);

        if (!projectLocations.isEmpty()) {
            renderSectionTitle("Project locations", textOutput);
            textOutput.println();

            projectLocations.sort(Comparator.comparing(ProjectDetails::getDisplayName));
            for (ProjectDetails project : projectLocations) {
                textOutput.withStyle(Identifier).text(project.getDisplayName());
                textOutput.withStyle(Description).text(" - ").text(File.separatorChar).text(project.getRelativeProjectDir());
                textOutput.println();
            }
        }
    }

    private void gatherLocations(ProjectReportModel model, List<ProjectDetails> locations) {
        if (!model.isRootProject) {
            locations.add(model.project);
        }
        for (ProjectReportModel child : model.children) {
            gatherLocations(child, locations);
        }
    }

    private void renderIncludedBuilds(ProjectReportModel model, StyledTextOutput textOutput) {
        if (model.isRootProject) {
            int index = 0;
            if (!model.includedBuildIdentityPaths.isEmpty()) {
                GraphRenderer renderer = new GraphRenderer(textOutput);
                renderSectionTitle("Included builds", textOutput);
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

    private void renderHelp(ProjectReportModel model, StyledTextOutput textOutput) {
        BuildClientMetaData metaData = getClientMetaData();

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
