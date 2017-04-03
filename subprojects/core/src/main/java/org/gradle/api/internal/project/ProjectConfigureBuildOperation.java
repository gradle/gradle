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

public class ProjectConfigureBuildOperation {

    private final BuildOperationExecutor buildOperationExecutor;
    private final Project project;

    public ProjectConfigureBuildOperation(Project project, BuildOperationExecutor buildOperationExecutor) {
        this.project = project;
        this.buildOperationExecutor = buildOperationExecutor;
    }

    public Project runConfigureClosure(final Closure<? super Project> configureClosure) {
        Action<BuildOperationContext> buildOperationAction = new Action<BuildOperationContext>() {
            @Override
            public void execute(BuildOperationContext buildOperationContext) {
                ConfigureUtil.configure(configureClosure, project);
            }
        };
        return runBuildOperationAction(buildOperationAction);
    }

    public Project runConfigureAction(final Action<? super Project> configureAction) {
        Action<BuildOperationContext> buildOperationAction = new Action<BuildOperationContext>() {
            @Override
            public void execute(BuildOperationContext buildOperationContext) {
                Actions.with(project, configureAction);
            }
        };
        return runBuildOperationAction(buildOperationAction);
    }

    public Project runBuildOperationAction(Action<? super BuildOperationContext> buildOperationAction) {
        buildOperationExecutor.run(getBuildOperationDetails(), buildOperationAction);
        return project;
    }

    private BuildOperationDetails getBuildOperationDetails() {
        String name = getProjectPath();
        return BuildOperationDetails.displayName("Configure " + name).name(StringUtils.capitalize(name)).build();
    }

    private String getProjectPath() {
        ProjectInternal projectInternal = (ProjectInternal) project;
        if (project.getParent() == null && projectInternal.getGradle().findIdentityPath() == null) {
            return "project " + project.getName();
        }
        return "project " + projectInternal.getIdentityPath().toString();
    }
}
