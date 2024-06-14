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

import org.gradle.api.Action;
import org.gradle.api.IsolatedAction;
import org.gradle.api.Project;
import org.gradle.api.internal.DefaultMutationGuard;
import org.gradle.api.internal.MutationGuard;
import org.gradle.api.internal.WithMutationGuard;
import org.gradle.internal.Actions;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationRunner;
import org.gradle.internal.operations.RunnableBuildOperation;

import javax.annotation.Nullable;
import java.util.Collections;

public class BuildOperationCrossProjectConfigurator implements CrossProjectConfigurator, WithMutationGuard {

    private final BuildOperationRunner buildOperationRunner;
    private final MutationGuard mutationGuard = new DefaultMutationGuard();

    public BuildOperationCrossProjectConfigurator(BuildOperationRunner buildOperationRunner) {
        this.buildOperationRunner = buildOperationRunner;
    }

    @Override
    public void project(
        ProjectInternal project,
        Action<? super Project> configureAction,
        @Nullable IsolatedAction<? super Project> allprojectsAction
    ) {
        runProjectConfigureAction(project, configureAction, allprojectsAction);
    }

    @Override
    public void subprojects(
        Iterable<? extends ProjectInternal> projects,
        Action<? super Project> configureAction,
        @Nullable IsolatedAction<? super Project> allprojectsAction
    ) {
        runBlockConfigureAction(SUBPROJECTS_DETAILS, projects, configureAction, allprojectsAction);
    }

    @Override
    public void allprojects(
        Iterable<? extends ProjectInternal> projects,
        Action<? super Project> configureAction,
        @Nullable IsolatedAction<? super Project> allprojectsAction
    ) {
        runBlockConfigureAction(ALLPROJECTS_DETAILS, projects, configureAction, allprojectsAction);
    }

    @Override
    public void rootProject(ProjectInternal project, Action<? super Project> buildOperationRunner, @Nullable IsolatedAction<? super Project> allprojectsAction) {
        runBlockConfigureAction(ROOT_PROJECT_DETAILS, Collections.singleton(project), buildOperationRunner, allprojectsAction);
    }

    private void runBlockConfigureAction(
        final BuildOperationDescriptor.Builder details,
        final Iterable<? extends ProjectInternal> projects,
        final Action<? super Project> configureAction,
        @Nullable final IsolatedAction<? super Project> allprojectsAction
    ) {
        buildOperationRunner.run(new BlockConfigureBuildOperation(details, projects, configureAction, allprojectsAction));
    }

    private void runProjectConfigureAction(
        final ProjectInternal project,
        final Action<? super Project> configureAction,
        @Nullable final IsolatedAction<? super Project> allprojectsAction
    ) {
        project.getOwner().applyToMutableState(p -> buildOperationRunner.run(new CrossConfigureProjectBuildOperation(project) {
            @Override
            public void run(BuildOperationContext context) {
                Action<? super Project> action = allprojectsAction == null
                    ? configureAction
                    : (Action<Project>) p -> {
                        allprojectsAction.execute(p);
                        configureAction.execute(p);
                    };
                Actions.with(project, mutationGuard.withMutationEnabled(action));
            }
        }));
    }

    @Override
    public MutationGuard getMutationGuard() {
        return mutationGuard;
    }

    private final static BuildOperationDescriptor.Builder ALLPROJECTS_DETAILS = computeConfigurationBlockBuildOperationDetails("allprojects");
    private final static BuildOperationDescriptor.Builder SUBPROJECTS_DETAILS = computeConfigurationBlockBuildOperationDetails("subprojects");
    private final static BuildOperationDescriptor.Builder ROOT_PROJECT_DETAILS = computeConfigurationBlockBuildOperationDetails("rootProject");

    private static BuildOperationDescriptor.Builder computeConfigurationBlockBuildOperationDetails(String configurationBlockName) {
        return BuildOperationDescriptor.displayName("Execute '" + configurationBlockName + " {}' action").name(configurationBlockName);
    }

    private class BlockConfigureBuildOperation implements RunnableBuildOperation {

        private final BuildOperationDescriptor.Builder details;
        private final Iterable<? extends ProjectInternal> projects;
        private final Action<? super Project> configureAction;
        @Nullable
        private final IsolatedAction<? super Project> allprojectsAction;

        private BlockConfigureBuildOperation(
            BuildOperationDescriptor.Builder details,
            Iterable<? extends ProjectInternal> projects,
            Action<? super Project> configureAction,
            @Nullable IsolatedAction<? super Project> allprojectsAction
        ) {
            this.details = details;
            this.projects = projects;
            this.configureAction = configureAction;
            this.allprojectsAction = allprojectsAction;
        }

        @Override
        public BuildOperationDescriptor.Builder description() {
            return details;
        }

        @Override
        public void run(BuildOperationContext context) {
            for (ProjectInternal project : projects) {
                runProjectConfigureAction(project, configureAction, allprojectsAction);
            }
        }
    }

    private static abstract class CrossConfigureProjectBuildOperation implements RunnableBuildOperation {
        private final Project project;

        private CrossConfigureProjectBuildOperation(Project project) {
            this.project = project;
        }

        @Override
        public BuildOperationDescriptor.Builder description() {
            String name = "Cross-configure project " + ((ProjectInternal) project).getIdentityPath();
            return BuildOperationDescriptor.displayName(name);
        }
    }
}
