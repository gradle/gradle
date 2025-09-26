/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.launcher.exec;

import org.gradle.StartParameter;
import org.gradle.api.JavaVersion;
import org.gradle.api.internal.BuildDefinition;
import org.gradle.api.logging.configuration.ShowStacktrace;
import org.gradle.api.problems.internal.InternalProblems;
import org.gradle.api.problems.internal.ProblemsProgressEventEmitterHolder;
import org.gradle.internal.build.BuildStateRegistry;
import org.gradle.internal.build.RootBuildState;
import org.gradle.internal.buildtree.BuildActionRunner;
import org.gradle.internal.buildtree.BuildModelParameters;
import org.gradle.internal.buildtree.BuildTreeLifecycleListener;
import org.gradle.internal.deprecation.DeprecationLogger;
import org.gradle.internal.featurelifecycle.LoggingDeprecatedFeatureHandler;
import org.gradle.internal.invocation.BuildAction;
import org.gradle.internal.jvm.SupportedJavaVersions;
import org.gradle.internal.operations.BuildOperationProgressEventEmitter;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;
import org.gradle.internal.work.ProjectParallelExecutionController;
import org.gradle.problems.buildtree.ProblemStream;
import org.gradle.util.GradleVersion;
import org.gradle.util.internal.VersionNumber;

/**
 * Prepares the build-tree services and runs the build action on behalf of the root build.
 */
@ServiceScope(Scope.BuildTree.class)
public class RootBuildLifecycleBuildActionExecutor {

    private final BuildModelParameters buildModelParameters;
    private final ProjectParallelExecutionController projectParallelExecutionController;
    private final BuildTreeLifecycleListener lifecycleListener;
    private final InternalProblems problemsService;
    private final BuildOperationProgressEventEmitter eventEmitter;
    private final StartParameter startParameter;
    private final ProblemStream problemsStream;
    private final BuildActionRunner buildActionRunner;
    private final BuildStateRegistry buildStateRegistry;

    private boolean executed;

    public RootBuildLifecycleBuildActionExecutor(
        BuildModelParameters buildModelParameters,
        ProjectParallelExecutionController projectParallelExecutionController,
        BuildTreeLifecycleListener lifecycleListener,
        InternalProblems problemsService,
        BuildOperationProgressEventEmitter eventEmitter,
        StartParameter startParameter,
        ProblemStream problemsStream,
        BuildStateRegistry buildStateRegistry,
        BuildActionRunner buildActionRunner
    ) {
        this.buildModelParameters = buildModelParameters;
        this.projectParallelExecutionController = projectParallelExecutionController;
        this.lifecycleListener = lifecycleListener;
        this.problemsService = problemsService;
        this.eventEmitter = eventEmitter;
        this.startParameter = startParameter;
        this.problemsStream = problemsStream;
        this.buildActionRunner = buildActionRunner;
        this.buildStateRegistry = buildStateRegistry;
    }

    /**
     * Creates the root build state and executes the given action against it.
     * <p>
     * When this method returns, all user code will have been completed, including 'build finished' hooks.
     */
    public BuildActionRunner.Result execute(BuildAction action) {
        if (executed) {
            throw new IllegalStateException("Cannot execute a root build action more than once per build tree.");
        }
        executed = true;

        projectParallelExecutionController.startProjectExecution(buildModelParameters.isParallelProjectExecution());
        try {
            lifecycleListener.afterStart();
            try {
                ProblemsProgressEventEmitterHolder.init(problemsService);
                initDeprecationLogging();
                maybeNagOnDeprecatedJavaRuntimeVersion();
                RootBuildState rootBuild = buildStateRegistry.createRootBuild(BuildDefinition.fromStartParameter(action.getStartParameter(), null));
                return rootBuild.run(buildController -> buildActionRunner.run(action, buildController));
            } finally {
                lifecycleListener.beforeStop();
            }
        } finally {
            projectParallelExecutionController.finishProjectExecution();
        }
    }

    private void initDeprecationLogging() {
        ShowStacktrace showStacktrace = startParameter.getShowStacktrace();
        LoggingDeprecatedFeatureHandler.setTraceLoggingEnabled(showStacktrace.equals(ShowStacktrace.ALWAYS) || showStacktrace.equals(ShowStacktrace.ALWAYS_FULL));
        DeprecationLogger.init(startParameter.getWarningMode(), eventEmitter, problemsService, problemsStream);
    }

    private static void maybeNagOnDeprecatedJavaRuntimeVersion() {
        int currentMajor = Integer.parseInt(JavaVersion.current().getMajorVersion());
        if (currentMajor < SupportedJavaVersions.FUTURE_MINIMUM_DAEMON_JAVA_VERSION) {
            // Note: this deprecation is unreachable while the future version is the same as MINIMUM_DAEMON_JAVA_VERSION, we keep it for ease of future upgrades
            int currentMajorGradleVersion = VersionNumber.parse(GradleVersion.current().getVersion()).getMajor();
            DeprecationLogger.deprecateAction(String.format("Executing Gradle on JVM versions %d and lower", SupportedJavaVersions.FUTURE_MINIMUM_DAEMON_JAVA_VERSION - 1))
                .withContext(String.format("Use JVM %d or greater to execute Gradle. Projects can continue to use older JVM versions via toolchains.", SupportedJavaVersions.FUTURE_MINIMUM_DAEMON_JAVA_VERSION))
                .willBecomeAnErrorInNextMajorGradleVersion()
                .withUpgradeGuideSection(currentMajorGradleVersion, "minimum_daemon_jvm_version")
                .nagUser();
        }
    }
}
