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
import org.gradle.api.Project;
import org.gradle.internal.Actions;
import org.gradle.internal.exceptions.Contextual;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.RunnableBuildOperation;

import java.util.Collections;

public class BuildOperationCrossProjectConfigurator implements CrossProjectConfigurator {

    private final BuildOperationExecutor buildOperationExecutor;
    private final ThreadLocal<Boolean> allowExecution = new ThreadLocal<Boolean>() {
        @Override
        protected Boolean initialValue() {
            return true;
        }
    };

    public BuildOperationCrossProjectConfigurator(BuildOperationExecutor buildOperationExecutor) {
        this.buildOperationExecutor = buildOperationExecutor;
    }

    @Override
    public Project project(Project project, Action<? super Project> configureAction) {
        runProjectConfigureAction(project, configureAction);
        return project;
    }

    @Override
    public void subprojects(Iterable<Project> projects, Action<? super Project> configureAction) {
        runBlockConfigureAction(BlockConfigureBuildOperation.SUBPROJECTS_DETAILS, projects, configureAction);
    }

    @Override
    public void allprojects(Iterable<Project> projects, Action<? super Project> configureAction) {
        runBlockConfigureAction(BlockConfigureBuildOperation.ALLPROJECTS_DETAILS, projects, configureAction);
    }

    @Override
    public Project rootProject(Project project, Action<Project> buildOperationExecutor) {
        runBlockConfigureAction(BlockConfigureBuildOperation.ROOT_PROJECT_DETAILS, Collections.singleton(project), buildOperationExecutor);
        return project;
    }

    private void runBlockConfigureAction(final BuildOperationDescriptor.Builder details, final Iterable<Project> projects, final Action<? super Project> configureAction) {
        buildOperationExecutor.run(new BlockConfigureBuildOperation(details, projects) {
            @Override
            protected void doRunProjectConfigure(Project project) {
                runProjectConfigureAction(project, configureAction);
            }
        });
    }

    private void runProjectConfigureAction(final Project project, final Action<? super Project> configureAction) {
        buildOperationExecutor.run(new CrossConfigureProjectBuildOperation(project) {
            @Override
            public void run(BuildOperationContext context) {
                Actions.with(project, withCrossProjectConfigurationEnabled(configureAction));
            }
        });
    }

    @Override
    public void assertCrossProjectConfigurationAllowed(String methodName, Project target) {
        if (!isCrossConfigurationAllowed()) {
            throw createIllegalStateException(methodName, target);
        }
    }

    @Override
    public <T> Action<T> withCrossProjectConfigurationDisabled(final Action<? super T> action) {
        return executeActionWithMutation(action, false);
    }

    // TODO: Promote to CrossProjectConfigurator interface if this is needed elsewhere.
    public <T> Action<T> withCrossProjectConfigurationEnabled(final Action<? super T> action) {
        return executeActionWithMutation(action, true);
    }

    private <T> Action<T> executeActionWithMutation(final Action<? super T> action, final boolean allowMutationMethods) {
        return new Action<T>() {
            @Override
            public void execute(T t) {
                boolean save = allowExecution.get();
                allowExecution.set(allowMutationMethods);
                try {
                    action.execute(t);
                } finally {
                    allowExecution.set(save);
                }
            }
        };
    }

    private IllegalStateException createIllegalStateException(String methodName, Project target) {
        return new IllegalCrossProjectConfigurationException(String.format("%s on %s cannot be executed in the current context.", methodName, target));
    }

    private boolean isCrossConfigurationAllowed() {
        return allowExecution.get();
    }

    @Contextual
    private static class IllegalCrossProjectConfigurationException extends IllegalStateException {
        public IllegalCrossProjectConfigurationException(String message) {
            super(message);
        }
    }

    private static abstract class BlockConfigureBuildOperation implements RunnableBuildOperation {

        private final static String ALLPROJECTS = "allprojects";
        private final static String SUBPROJECTS = "subprojects";
        private final static String ROOTPROJECT = "rootProject";

        private final static BuildOperationDescriptor.Builder ALLPROJECTS_DETAILS = computeConfigurationBlockBuildOperationDetails(ALLPROJECTS);
        private final static BuildOperationDescriptor.Builder SUBPROJECTS_DETAILS = computeConfigurationBlockBuildOperationDetails(SUBPROJECTS);
        private final static BuildOperationDescriptor.Builder ROOT_PROJECT_DETAILS = computeConfigurationBlockBuildOperationDetails(ROOTPROJECT);

        private final BuildOperationDescriptor.Builder details;
        private final Iterable<Project> projects;

        private BlockConfigureBuildOperation(BuildOperationDescriptor.Builder details, Iterable<Project> projects) {
            this.details = details;
            this.projects = projects;
        }

        private static BuildOperationDescriptor.Builder computeConfigurationBlockBuildOperationDetails(String configurationBlockName) {
            return BuildOperationDescriptor.displayName("Execute '" + configurationBlockName + " {}' action").name(configurationBlockName);
        }

        @Override
        public BuildOperationDescriptor.Builder description() {
            return details;
        }

        @Override
        public void run(BuildOperationContext context) {
            for (Project project : projects) {
                doRunProjectConfigure(project);
            }
        }

        abstract void doRunProjectConfigure(Project project);
    }

    private static abstract class CrossConfigureProjectBuildOperation implements RunnableBuildOperation {
        private Project project;

        private CrossConfigureProjectBuildOperation(Project project) {
            this.project = project;
        }

        @Override
        public BuildOperationDescriptor.Builder description() {
            String name = "Cross-configure project " + ((ProjectInternal) project).getIdentityPath().toString();
            return BuildOperationDescriptor.displayName(name);
        }
    }
}
