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
import org.gradle.api.BuildCancelledException;
import org.gradle.api.ProjectConfigurationException;
import org.gradle.api.ProjectEvaluationListener;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.project.ProjectStateInternal;
import org.gradle.initialization.BuildCancellationToken;
import org.gradle.internal.operations.BuildOperationCategory;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.RunnableBuildOperation;
import org.gradle.util.Path;

import java.io.File;

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
 * The use of term “evaluate” is a legacy constraint.
 * Project evaluation is synonymous with “project configuration” (the latter being the preferred term).
 *
 * @see ProjectEvaluationListener
 */
public class LifecycleProjectEvaluator implements ProjectEvaluator {
    private final BuildOperationExecutor buildOperationExecutor;
    private final ProjectEvaluator delegate;
    private final BuildCancellationToken cancellationToken;

    public LifecycleProjectEvaluator(BuildOperationExecutor buildOperationExecutor, ProjectEvaluator delegate, BuildCancellationToken cancellationToken) {
        this.buildOperationExecutor = buildOperationExecutor;
        this.delegate = delegate;
        this.cancellationToken = cancellationToken;
    }

    @Override
    public void evaluate(final ProjectInternal project, final ProjectStateInternal state) {
        if (state.isUnconfigured()) {
            if (cancellationToken.isCancellationRequested()) {
                throw new BuildCancelledException();
            }
            buildOperationExecutor.run(new EvaluateProject(project, state));
        }
    }

    private static void addConfigurationFailure(ProjectInternal project, ProjectStateInternal state, Exception e, BuildOperationContext ctx) {
        ProjectConfigurationException exception = wrapException(project, e);
        ctx.failed(exception);
        state.failed(exception);
    }

    private static ProjectConfigurationException wrapException(ProjectInternal project, Exception e) {
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
            project.getOwner().applyToMutableState(p -> {
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
            });
        }

        @Override
        public BuildOperationDescriptor.Builder description() {
            return configureProjectBuildOperationBuilderFor(this.project);
        }
    }

    public static BuildOperationDescriptor.Builder configureProjectBuildOperationBuilderFor(ProjectInternal projectInternal) {
        Path identityPath = projectInternal.getIdentityPath();
        String displayName = "Configure project " + identityPath;

        String progressDisplayName = identityPath.toString();
        if (identityPath.equals(Path.ROOT)) {
            progressDisplayName = "root project";
        }

        return BuildOperationDescriptor.displayName(displayName)
            .metadata(BuildOperationCategory.CONFIGURE_PROJECT)
            .progressDisplayName(progressDisplayName)
            .details(new ConfigureProjectDetails(projectInternal.getProjectPath(), projectInternal.getGradle().getIdentityPath(), projectInternal.getRootDir()));
    }

    private static class ConfigureProjectDetails implements ConfigureProjectBuildOperationType.Details {

        private final Path buildPath;
        private final File rootDir;
        private final Path projectPath;

        public ConfigureProjectDetails(Path projectPath, Path buildPath, File rootDir) {
            this.projectPath = projectPath;
            this.buildPath = buildPath;
            this.rootDir = rootDir;
        }

        @Override
        public String getProjectPath() {
            return projectPath.getPath();
        }

        @Override
        public String getBuildPath() {
            return buildPath.getPath();
        }

        @Override
        public File getRootDir() {
            return rootDir;
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
                .details(new NotifyProjectBeforeEvaluatedDetails(
                    project.getProjectPath(),
                    project.getGradle().getIdentityPath()
                ));
        }
    }

    private static class NotifyProjectBeforeEvaluatedDetails implements NotifyProjectBeforeEvaluatedBuildOperationType.Details {

        private final Path buildPath;
        private final Path projectPath;

        NotifyProjectBeforeEvaluatedDetails(Path projectPath, Path buildPath) {
            this.projectPath = projectPath;
            this.buildPath = buildPath;
        }

        @Override
        public String getProjectPath() {
            return projectPath.getPath();
        }

        @Override
        public String getBuildPath() {
            return buildPath.getPath();
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
                    addConfigurationFailure(project, state, e, context);
                    return;
                }
            } while (nextBatch != null);

            context.setResult(NotifyProjectAfterEvaluatedBuildOperationType.RESULT);
        }

        @Override
        public BuildOperationDescriptor.Builder description() {
            return BuildOperationDescriptor.displayName("Notify afterEvaluate listeners of " + project.getIdentityPath())
                .details(new NotifyProjectAfterEvaluatedDetails(
                    project.getProjectPath(),
                    project.getGradle().getIdentityPath()
                ));
        }
    }

    private static class NotifyProjectAfterEvaluatedDetails implements NotifyProjectAfterEvaluatedBuildOperationType.Details {

        private final Path buildPath;
        private final Path projectPath;

        NotifyProjectAfterEvaluatedDetails(Path projectPath, Path buildPath) {
            this.projectPath = projectPath;
            this.buildPath = buildPath;
        }

        @Override
        public String getProjectPath() {
            return projectPath.getPath();
        }

        @Override
        public String getBuildPath() {
            return buildPath.getPath();
        }

    }
}
