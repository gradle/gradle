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
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.progress.BuildOperationDetails;
import org.gradle.internal.progress.BuildOperationExecutor;

public class ProjectConfigureBlockBuildOperation {

    private final BuildOperationExecutor buildOperationExecutor;
    private final String configurationBlockName;
    private final Iterable<Project> projects;

    public ProjectConfigureBlockBuildOperation(String configurationBlockName, Iterable<Project> projects, BuildOperationExecutor buildOperationExecutor) {
        this.configurationBlockName = configurationBlockName;
        this.projects = projects;
        this.buildOperationExecutor = buildOperationExecutor;
    }

    public void runConfigureClosure(final Closure<? super Project> configureClosure) {
        Action<BuildOperationContext> buildOperationAction = new Action<BuildOperationContext>() {
            @Override
            public void execute(BuildOperationContext buildOperationContext) {
                for (Project project : projects) {
                    new ProjectConfigureBuildOperation(project, buildOperationExecutor).runConfigureClosure(configureClosure);
                }
            }
        };
        runBuildOperationAction(buildOperationAction);
    }

    public void runConfigureAction(final Action<? super Project> configureAction) {
        Action<BuildOperationContext> buildOperationAction = new Action<BuildOperationContext>() {
            @Override
            public void execute(BuildOperationContext buildOperationContext) {
                for (Project project : projects) {
                    new ProjectConfigureBuildOperation(project, buildOperationExecutor).runConfigureAction(configureAction);
                }
            }
        };
        runBuildOperationAction(buildOperationAction);
    }

    public void runBuildOperationAction(Action<? super BuildOperationContext> buildOperationAction) {
        buildOperationExecutor.run(getBuildOperationDetails(), buildOperationAction);
    }

    private BuildOperationDetails getBuildOperationDetails() {
        return BuildOperationDetails.displayName("Executing '" + configurationBlockName + " {}' action").name(configurationBlockName).build();
    }
}
