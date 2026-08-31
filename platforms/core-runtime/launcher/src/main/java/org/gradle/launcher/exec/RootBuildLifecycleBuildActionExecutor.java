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

import org.gradle.api.JavaVersion;
import org.gradle.api.internal.BuildDefinition;
import org.gradle.api.internal.StartParameterInternal;
import org.gradle.api.logging.configuration.ShowStacktrace;
import org.gradle.api.problems.internal.ProblemsInternal;
import org.gradle.initialization.StartParameterBuildOptions;
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
    private final ProblemsInternal problemsService;
    private final BuildOperationProgressEventEmitter eventEmitter;
    private final ProblemStream problemsStream;
    private final BuildActionRunner buildActionRunner;
    private final BuildStateRegistry buildStateRegistry;

    private boolean executed;

    public RootBuildLifecycleBuildActionExecutor(
        BuildModelParameters buildModelParameters,
        ProjectParallelExecutionController projectParallelExecutionController,
        BuildTreeLifecycleListener lifecycleListener,
        ProblemsInternal problemsService,
        BuildOperationProgressEventEmitter eventEmitter,
        ProblemStream problemsStream,
        BuildStateRegistry buildStateRegistry,
        BuildActionRunner buildActionRunner
    ) {
        this.buildModelParameters = buildModelParameters;
        this.projectParallelExecutionController = projectParallelExecutionController;
        this.lifecycleListener = lifecycleListener;
        this.problemsService = problemsService;
        this.eventEmitter = eventEmitter;
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
            StartParameterInternal startParameter = action.getStartParameter();
            try {
                initDeprecationLogging(startParameter);
                maybeNagOnDeprecatedJavaRuntimeVersion();
                maybeNagOnImplicitParallelModelBuildingOptIn(startParameter);
                RootBuildState rootBuild = buildStateRegistry.createRootBuild(BuildDefinition.fromStartParameter(startParameter, null));
                return rootBuild.run(buildController -> buildActionRunner.run(action, buildController));
            } finally {
                // Since continuous builds reuse the same StartParameter for multiple build trees.
                startParameter.clearMutationListener();
                lifecycleListener.beforeStop();
            }
        } finally {
            projectParallelExecutionController.finishProjectExecution();
        }
    }

    private void initDeprecationLogging(StartParameterInternal startParameter) {
        ShowStacktrace showStacktrace = startParameter.getShowStacktrace();
        LoggingDeprecatedFeatureHandler.setTraceLoggingEnabled(showStacktrace.equals(ShowStacktrace.ALWAYS) || showStacktrace.equals(ShowStacktrace.ALWAYS_FULL));
        DeprecationLogger.init(startParameter.getWarningMode(), eventEmitter, problemsService, problemsStream);
    }

    /**
     * Deprecates relying on the legacy default where parallel model building (used by the Tooling API,
     * e.g. during IDE sync) is derived from the {@code org.gradle.parallel} property.
     * <p>
     * This applies only to Vintage model building, which is the only mode where the implicit
     * {@code org.gradle.parallel} -> parallel model building link exists.
     * <p>
     * "Vintage" refers to the resolved {@link BuildModelParameters} mode, not the user-facing opt-in:
     * model building with Configuration Cache enabled currently falls back to Vintage (models cannot be
     * cached yet), so such builds are in scope of this deprecation. Isolated Projects model building is not.
     * <p>
     * The nag is intentionally emitted regardless of the {@code org.gradle.tooling.parallel.ignore-legacy-default}
     * system property: in a future major, having {@code org.gradle.parallel} enabled without an explicit value
     * for {@code org.gradle.tooling.parallel} during model building will be an error, so users relying on the
     * default must make it explicit now to avoid breaking on upgrade.
     */
    private void maybeNagOnImplicitParallelModelBuildingOptIn(StartParameterInternal startParameter) {
        boolean relyingOnLegacyDefault = buildModelParameters.isModelBuilding()
            && buildModelParameters.isVintage()
            && !startParameter.getParallelToolingModelBuilding().isExplicit()
            && startParameter.isParallelProjectExecutionEnabled();
        if (relyingOnLegacyDefault) {
            String toolingParallelProperty = StartParameterBuildOptions.ParallelToolingModelBuildingOption.PROPERTY_NAME;
            DeprecationLogger.deprecateBehaviour("Relying on the default value of the '" + toolingParallelProperty + "' property while 'org.gradle.parallel' is enabled.")
                .withContext("Whether the Tooling API builds project models in parallel (for example, during IDE sync) should be controlled explicitly via '" + toolingParallelProperty + "'.")
                .withAdvice("Set '" + toolingParallelProperty + "' to 'true' or 'false' explicitly.")
                .willBecomeAnErrorInGradle10()
                .withUpgradeGuideSection(9, "deprecate_implicit_parallel_model_building")
                .nagUser();
        }
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
