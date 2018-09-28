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
package org.gradle.configuration.project;

import org.gradle.api.Action;
import org.gradle.api.ProjectConfigurationException;
import org.gradle.api.ProjectEvaluationListener;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.project.ProjectStateInternal;
import org.gradle.api.logging.configuration.ShowStacktrace;
import org.gradle.internal.operations.BuildOperationCategory;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.RunnableBuildOperation;
import org.gradle.util.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Notifies listeners before and after delegating to the provided delegate to the actual evaluation,
 * wrapping the work in build operations.
 *
 * The build operation structure is:
 *
 * - Evaluate project
 * -- Notify before evaluate
 * -- Notify after evaluate
 *
 * Notably, there is no explicit operation for just the project.evaluate() (which is where the build scripts etc. run).
 * However, in practice there is usually an operation for evaluating the project's build script.
 *
 * The before/after evaluate operations are fired regardless whether anyone is actually listening.
 * This may change in future versions.
 *
 * The use of term “evaluate” is a legacy constraint.
 * Project evaluation is synonymous with “project configuration” (the latter being the preferred term).
 *
 * @see ProjectEvaluationListener
 */
public class LifecycleProjectEvaluator implements ProjectEvaluator {

    private static final Logger LOGGER = LoggerFactory.getLogger(LifecycleProjectEvaluator.class);

    private final BuildOperationExecutor buildOperationExecutor;
    private final ProjectEvaluator delegate;

    public LifecycleProjectEvaluator(BuildOperationExecutor buildOperationExecutor, ProjectEvaluator delegate) {
        this.buildOperationExecutor = buildOperationExecutor;
        this.delegate = delegate;
    }

    public void evaluate(final ProjectInternal project, final ProjectStateInternal state) {
        if (state.isUnconfigured()) {
            buildOperationExecutor.run(new EvaluateProject(project, state));
        }
    }

    private static void addConfigurationFailure(ProjectInternal project, ProjectStateInternal state, Exception e, BuildOperationContext ctx) {
        Exception exception = wrapException(project, e);
        ctx.failed(exception);
        state.failed(exception);
    }

    private static Exception wrapException(ProjectInternal project, Exception e) {
        return new ProjectConfigurationException(
            String.format("A problem occurred configuring %s.", project.getDisplayName()), e
        );
    }

    private class EvaluateProject implements RunnableBuildOperation {

        private final ProjectInternal project;
        private final ProjectStateInternal state;

        private EvaluateProject(ProjectInternal project, ProjectStateInternal state) {
            this.project = project;
            this.state = state;
        }

        @Override
        public void run(final BuildOperationContext context) {
            project.getMutationState().withMutableState(new Runnable() {
                @Override
                public void run() {
                    // Note: beforeEvaluate and afterEvaluate ops do not throw, instead mark state as failed
                    try {
                        state.toBeforeEvaluate();
                        buildOperationExecutor.run(new NotifyBeforeEvaluate(project, state));

                        if (!state.hasFailure()) {
                            state.toEvaluate();
                            try {
                                delegate.evaluate(project, state);
                            } catch (Exception e) {
                                addConfigurationFailure(project, state, e, context);
                            } finally {
                                state.toAfterEvaluate();
                                buildOperationExecutor.run(new NotifyAfterEvaluate(project, state));
                            }
                        }

                        if (state.hasFailure()) {
                            state.rethrowFailure();
                        } else {
                            context.setResult(ConfigureProjectBuildOperationType.RESULT);
                        }
                    } finally {
                        state.configured();
                    }
                }
            });
        }

        @Override
        public BuildOperationDescriptor.Builder description() {
            Path identityPath = project.getIdentityPath();
            String displayName = "Configure project " + identityPath.toString();

            String progressDisplayName = identityPath.toString();
            if (identityPath.equals(Path.ROOT)) {
                progressDisplayName = "root project";
            }

            return BuildOperationDescriptor.displayName(displayName)
                .operationType(BuildOperationCategory.CONFIGURE_PROJECT)
                .progressDisplayName(progressDisplayName)
                .details(new ConfigureProjectBuildOperationType.DetailsImpl(project.getProjectPath(), project.getGradle().getIdentityPath()));
        }
    }

    private static class NotifyBeforeEvaluate implements RunnableBuildOperation {

        private final ProjectInternal project;
        private final ProjectStateInternal state;

        private NotifyBeforeEvaluate(ProjectInternal project, ProjectStateInternal state) {
            this.project = project;
            this.state = state;
        }

        @Override
        public void run(BuildOperationContext context) {
            try {
                project.getProjectEvaluationBroadcaster().beforeEvaluate(project);
                context.setResult(NotifyProjectBeforeEvaluatedBuildOperationType.RESULT);
            } catch (Exception e) {
                addConfigurationFailure(project, state, e, context);
            }
        }

        @Override
        public BuildOperationDescriptor.Builder description() {
            return BuildOperationDescriptor.displayName("Notify beforeEvaluate listeners of " + project.getIdentityPath())
                .details(new NotifyProjectBeforeEvaluatedBuildOperationType.DetailsImpl(
                    project.getProjectPath(),
                    project.getGradle().getIdentityPath()
                ));
        }
    }

    private static class NotifyAfterEvaluate implements RunnableBuildOperation {

        private final ProjectInternal project;
        private final ProjectStateInternal state;

        private NotifyAfterEvaluate(ProjectInternal project, ProjectStateInternal state) {
            this.project = project;
            this.state = state;
        }

        @Override
        public void run(BuildOperationContext context) {
            ProjectEvaluationListener nextBatch = project.getProjectEvaluationBroadcaster();
            Action<ProjectEvaluationListener> fireAction = new Action<ProjectEvaluationListener>() {
                @Override
                public void execute(ProjectEvaluationListener listener) {
                    listener.afterEvaluate(project, state);
                }
            };

            do {
                try {
                    nextBatch = project.stepEvaluationListener(nextBatch, fireAction);
                } catch (Exception e) {
                    if (state.hasFailure()) {
                        // Just log this failure, and pass the existing failure out in the project state
                        logError(e, project);
                        context.failed(wrapException(project, e));
                    } else {
                        addConfigurationFailure(project, state, e, context);
                    }
                    return;
                }
            } while (nextBatch != null);

            context.setResult(NotifyProjectAfterEvaluatedBuildOperationType.RESULT);
        }

        private void logError(Exception e, ProjectInternal project) {
            boolean logStackTraces = project.getGradle().getStartParameter().getShowStacktrace() != ShowStacktrace.INTERNAL_EXCEPTIONS;
            String infoMessage = "Project evaluation failed including an error in afterEvaluate {}.";
            if (logStackTraces) {
                LOGGER.error(infoMessage, e);
            } else {
                LOGGER.error(infoMessage + " Run with --stacktrace for details of the afterEvaluate {} error.");
            }
        }

        @Override
        public BuildOperationDescriptor.Builder description() {
            return BuildOperationDescriptor.displayName("Notify afterEvaluate listeners of " + project.getIdentityPath())
                .details(new NotifyProjectAfterEvaluatedBuildOperationType.DetailsImpl(
                    project.getProjectPath(),
                    project.getGradle().getIdentityPath()
                ));
        }
    }
}
