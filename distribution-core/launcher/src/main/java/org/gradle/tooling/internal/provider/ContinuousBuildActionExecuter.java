/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.tooling.internal.provider;

import org.gradle.api.execution.internal.TaskInputsListener;
import org.gradle.api.execution.internal.TaskInputsListeners;
import org.gradle.api.internal.file.FileSystemSubset;
import org.gradle.api.logging.LogLevel;
import org.gradle.deployment.internal.Deployment;
import org.gradle.deployment.internal.DeploymentInternal;
import org.gradle.deployment.internal.DeploymentRegistryInternal;
import org.gradle.execution.CancellableOperationManager;
import org.gradle.execution.DefaultCancellableOperationManager;
import org.gradle.execution.PassThruCancellableOperationManager;
import org.gradle.initialization.BuildCancellationToken;
import org.gradle.initialization.BuildRequestMetaData;
import org.gradle.initialization.ContinuousExecutionGate;
import org.gradle.initialization.DefaultContinuousExecutionGate;
import org.gradle.internal.buildevents.BuildStartedTime;
import org.gradle.internal.concurrent.ExecutorFactory;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.filewatch.DefaultFileWatcherEventListener;
import org.gradle.internal.filewatch.FileSystemChangeWaiter;
import org.gradle.internal.filewatch.FileSystemChangeWaiterFactory;
import org.gradle.internal.filewatch.FileWatcherEventListener;
import org.gradle.internal.filewatch.PendingChangesListener;
import org.gradle.internal.filewatch.SingleFirePendingChangesListener;
import org.gradle.internal.invocation.BuildAction;
import org.gradle.internal.logging.text.StyledTextOutput;
import org.gradle.internal.logging.text.StyledTextOutputFactory;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.internal.session.BuildSessionContext;
import org.gradle.internal.time.Clock;
import org.gradle.launcher.exec.BuildActionExecuter;
import org.gradle.launcher.exec.BuildActionParameters;
import org.gradle.launcher.exec.BuildActionResult;
import org.gradle.util.DisconnectableInputStream;

import java.util.function.Supplier;

public class ContinuousBuildActionExecuter implements BuildActionExecuter<BuildActionParameters, BuildSessionContext> {
    private final BuildActionExecuter<BuildActionParameters, BuildSessionContext> delegate;
    private final TaskInputsListeners inputsListeners;
    private final BuildRequestMetaData requestMetaData;
    private final OperatingSystem operatingSystem;
    private final BuildCancellationToken cancellationToken;
    private final DeploymentRegistryInternal deploymentRegistry;
    private final ListenerManager listenerManager;
    private final BuildStartedTime buildStartedTime;
    private final Clock clock;
    private final FileSystemChangeWaiterFactory changeWaiterFactory;
    private final ExecutorFactory executorFactory;
    private final StyledTextOutput logger;

    public ContinuousBuildActionExecuter(
        FileSystemChangeWaiterFactory changeWaiterFactory,
        TaskInputsListeners inputsListeners,
        StyledTextOutputFactory styledTextOutputFactory,
        ExecutorFactory executorFactory,
        BuildRequestMetaData requestMetaData,
        BuildCancellationToken cancellationToken,
        DeploymentRegistryInternal deploymentRegistry,
        ListenerManager listenerManager,
        BuildStartedTime buildStartedTime,
        Clock clock,
        BuildActionExecuter<BuildActionParameters, BuildSessionContext> delegate
    ) {
        this.inputsListeners = inputsListeners;
        this.requestMetaData = requestMetaData;
        this.cancellationToken = cancellationToken;
        this.deploymentRegistry = deploymentRegistry;
        this.listenerManager = listenerManager;
        this.buildStartedTime = buildStartedTime;
        this.clock = clock;
        this.operatingSystem = OperatingSystem.current();
        this.executorFactory = executorFactory;
        this.changeWaiterFactory = changeWaiterFactory;
        this.logger = styledTextOutputFactory.create(ContinuousBuildActionExecuter.class, LogLevel.QUIET);
        this.delegate = delegate;
    }

    @Override
    public BuildActionResult execute(BuildAction action, BuildActionParameters actionParameters, BuildSessionContext buildSession) {
        if (actionParameters.isContinuous()) {
            DefaultContinuousExecutionGate alwaysOpenExecutionGate = new DefaultContinuousExecutionGate();
            final CancellableOperationManager cancellableOperationManager = createCancellableOperationManager(requestMetaData, cancellationToken);
            return executeMultipleBuilds(action, requestMetaData, actionParameters, buildSession, cancellationToken, cancellableOperationManager, alwaysOpenExecutionGate);
        } else {
            try {
                return delegate.execute(action, actionParameters, buildSession);
            } finally {
                final CancellableOperationManager cancellableOperationManager = createCancellableOperationManager(requestMetaData, cancellationToken);
                waitForDeployments(action, requestMetaData, actionParameters, buildSession, cancellationToken, cancellableOperationManager);
            }
        }
    }

    private CancellableOperationManager createCancellableOperationManager(BuildRequestMetaData requestContext, BuildCancellationToken cancellationToken) {
        final CancellableOperationManager cancellableOperationManager;
        if (requestContext.isInteractive()) {
            if (!(System.in instanceof DisconnectableInputStream)) {
                System.setIn(new DisconnectableInputStream(System.in));
            }
            DisconnectableInputStream inputStream = (DisconnectableInputStream) System.in;
            cancellableOperationManager = new DefaultCancellableOperationManager(executorFactory.create("Cancel signal monitor"), inputStream, cancellationToken);
        } else {
            cancellableOperationManager = new PassThruCancellableOperationManager(cancellationToken);
        }
        return cancellableOperationManager;
    }

    private void waitForDeployments(BuildAction action, BuildRequestMetaData requestContext, BuildActionParameters actionParameters, BuildSessionContext buildSession, BuildCancellationToken cancellationToken, CancellableOperationManager cancellableOperationManager) {
        if (!deploymentRegistry.getRunningDeployments().isEmpty()) {
            // Deployments are considered outOfDate until initial execution with file watching
            for (Deployment deployment : deploymentRegistry.getRunningDeployments()) {
                ((DeploymentInternal) deployment).outOfDate();
            }
            logger.println().println("Reloadable deployment detected. Entering continuous build.");
            resetBuildStartedTime();
            ContinuousExecutionGate deploymentRequestExecutionGate = deploymentRegistry.getExecutionGate();
            executeMultipleBuilds(action, requestContext, actionParameters, buildSession, cancellationToken, cancellableOperationManager, deploymentRequestExecutionGate);
        }
        cancellableOperationManager.closeInput();
    }

    private BuildActionResult executeMultipleBuilds(BuildAction action, BuildRequestMetaData requestContext, BuildActionParameters actionParameters, BuildSessionContext buildSession,
                                                    BuildCancellationToken cancellationToken, CancellableOperationManager cancellableOperationManager, ContinuousExecutionGate continuousExecutionGate) {
        BuildActionResult lastResult;
        while (true) {
            PendingChangesListener pendingChangesListener = listenerManager.getBroadcaster(PendingChangesListener.class);
            final FileSystemChangeWaiter waiter = changeWaiterFactory.createChangeWaiter(new SingleFirePendingChangesListener(pendingChangesListener), cancellationToken, continuousExecutionGate);
            try {
                lastResult = executeBuildAndAccumulateInputs(action, actionParameters, waiter, buildSession);

                if (!waiter.isWatching()) {
                    logger.println().withStyle(StyledTextOutput.Style.Failure).println("Exiting continuous build as no executed tasks declared file system inputs.");
                    return lastResult;
                } else {
                    cancellableOperationManager.monitorInput(operationToken -> {
                        FileWatcherEventListener reporter = new DefaultFileWatcherEventListener();
                        waiter.wait(
                            () -> logger.println().println("Waiting for changes to input files of tasks..." + determineExitHint(requestContext)),
                            reporter
                        );
                        if (!operationToken.isCancellationRequested()) {
                            reporter.reportChanges(logger);
                        }
                    });
                }
            } finally {
                waiter.stop();
            }

            if (cancellationToken.isCancellationRequested()) {
                break;
            } else {
                logger.println("Change detected, executing build...").println();
                resetBuildStartedTime();
            }
        }

        logger.println("Build cancelled.");
        return lastResult;
    }

    private void resetBuildStartedTime() {
        buildStartedTime.reset(clock.getCurrentTime());
    }

    private String determineExitHint(BuildRequestMetaData requestContext) {
        if (requestContext.isInteractive()) {
            if (operatingSystem.isWindows()) {
                return " (ctrl-d then enter to exit)";
            } else {
                return " (ctrl-d to exit)";
            }
        } else {
            return "";
        }
    }

    private BuildActionResult executeBuildAndAccumulateInputs(
        BuildAction action,
        BuildActionParameters actionParameters,
        FileSystemChangeWaiter waiter,
        BuildSessionContext buildSession
    ) {
        return withTaskInputsListener(
            (task, fileSystemInputs) -> waiter.watch(FileSystemSubset.of(fileSystemInputs)),
            () -> delegate.execute(action, actionParameters, buildSession)
        );
    }

    private <T> T withTaskInputsListener(TaskInputsListener listener, Supplier<T> supplier) {
        try {
            inputsListeners.addListener(listener);
            return supplier.get();
        } finally {
            inputsListeners.removeListener(listener);
        }
    }
}
