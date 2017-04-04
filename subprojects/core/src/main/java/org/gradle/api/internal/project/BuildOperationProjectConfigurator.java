/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.api.internal.project;

import groovy.lang.Closure;
import org.apache.commons.lang.StringUtils;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.internal.Actions;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.progress.BuildOperationDetails;
import org.gradle.internal.progress.BuildOperationExecutor;
import org.gradle.util.ConfigureUtil;

import java.util.Collections;

public class BuildOperationProjectConfigurator implements ProjectConfigurator {

    private final static String ALLPROJECTS = "allprojects";
    private final static String SUBPROJECTS = "subprojects";
    private final static String ROOTPROJECT = "rootProject";

    private final BuildOperationDetails allprojectsDetails = computeConfigurationBlockBuildOperationDetails(ALLPROJECTS);
    private final BuildOperationDetails subprojectsDetails = computeConfigurationBlockBuildOperationDetails(SUBPROJECTS);
    private final BuildOperationDetails rootProjectDetails = computeConfigurationBlockBuildOperationDetails(ROOTPROJECT);

    private final BuildOperationExecutor buildOperationExecutor;

    public BuildOperationProjectConfigurator(BuildOperationExecutor buildOperationExecutor) {
        this.buildOperationExecutor = buildOperationExecutor;
    }

    @Override
    public Project project(Project project, Closure<? super Project> configureClosure) {
        runProjectConfigureClosure(project, configureClosure);
        return project;
    }

    @Override
    public Project project(Project project, Action<? super Project> configureAction) {
        runProjectConfigureAction(project, configureAction);
        return project;
    }

    @Override
    public Project projectBuildOperationAction(Project project, Action<BuildOperationContext> configureAction) {
        runBuildOperation(computeProjectBuildOperationDetails(project), configureAction);
        return project;
    }

    @Override
    public void subprojects(Iterable<Project> projects, Closure<? super Project> configureClosure) {
        runBlockConfigureClosure(subprojectsDetails, projects, configureClosure);
    }

    @Override
    public void subprojects(Iterable<Project> projects, Action<? super Project> configureAction) {
        runBlockConfigureAction(subprojectsDetails, projects, configureAction);
    }

    @Override
    public void allprojects(Iterable<Project> projects, Closure<? super Project> configureClosure) {
        runBlockConfigureClosure(allprojectsDetails, projects, configureClosure);
    }

    @Override
    public void allprojects(Iterable<Project> projects, Action<? super Project> configureAction) {
        runBlockConfigureAction(allprojectsDetails, projects, configureAction);
    }

    @Override
    public Project rootProject(Project project, Action<Project> buildOperationExecutor) {
        runBlockConfigureAction(rootProjectDetails, Collections.singleton(project), buildOperationExecutor);
        return project;
    }

    public void runBlockConfigureClosure(BuildOperationDetails details, final Iterable<Project> projects, final Closure<? super Project> configureClosure) {
        Action<BuildOperationContext> buildOperationAction = new Action<BuildOperationContext>() {
            @Override
            public void execute(BuildOperationContext buildOperationContext) {
                for (Project project : projects) {
                    runProjectConfigureClosure(project, configureClosure);
                }
            }
        };
        runBuildOperation(details, buildOperationAction);
    }

    public void runBlockConfigureAction(BuildOperationDetails details, final Iterable<Project> projects, final Action<? super Project> configureAction) {
        Action<BuildOperationContext> buildOperationAction = new Action<BuildOperationContext>() {
            @Override
            public void execute(BuildOperationContext buildOperationContext) {
                for (Project project : projects) {
                    runProjectConfigureAction(project, configureAction);
                }
            }
        };
        runBuildOperation(details, buildOperationAction);
    }

    private void runProjectConfigureClosure(final Project project, final Closure<? super Project> configureClosure) {
        Action<BuildOperationContext> buildOperationAction = new Action<BuildOperationContext>() {
            @Override
            public void execute(BuildOperationContext buildOperationContext) {
                ConfigureUtil.configure(configureClosure, project);
            }
        };
        runBuildOperation(computeProjectBuildOperationDetails(project), buildOperationAction);
    }

    private void runProjectConfigureAction(final Project project, final Action<? super Project> configureAction) {
        Action<BuildOperationContext> buildOperationAction = new Action<BuildOperationContext>() {
            @Override
            public void execute(BuildOperationContext buildOperationContext) {
                Actions.with(project, configureAction);
            }
        };
        runBuildOperation(computeProjectBuildOperationDetails(project), buildOperationAction);
    }

    private void runBuildOperation(BuildOperationDetails details, Action<? super BuildOperationContext> buildOperationAction) {
        buildOperationExecutor.run(details, buildOperationAction);
    }

    private BuildOperationDetails computeConfigurationBlockBuildOperationDetails(String configurationBlockName) {
        return BuildOperationDetails.displayName("Executing '" + configurationBlockName + " {}' action").name(configurationBlockName).build();
    }

    private BuildOperationDetails computeProjectBuildOperationDetails(Project project) {
        String name = getProjectPath(project);
        return BuildOperationDetails.displayName("Configure " + name).name(StringUtils.capitalize(name)).build();
    }

    private String getProjectPath(Project project) {
        return "project " + ((ProjectInternal) project).getIdentityPath().toString();
    }
}
