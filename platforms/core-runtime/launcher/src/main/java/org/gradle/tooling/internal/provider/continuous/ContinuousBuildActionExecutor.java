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

package org.gradle.tooling.internal.provider.continuous;

import org.gradle.api.logging.LogLevel;
import org.gradle.deployment.internal.ContinuousExecutionGate;
import org.gradle.deployment.internal.DefaultContinuousExecutionGate;
import org.gradle.deployment.internal.Deployment;
import org.gradle.deployment.internal.DeploymentInternal;
import org.gradle.deployment.internal.DeploymentRegistryInternal;
import org.gradle.deployment.internal.PendingChangesListener;
import org.gradle.execution.CancellableOperationManager;
import org.gradle.execution.DefaultCancellableOperationManager;
import org.gradle.execution.PassThruCancellableOperationManager;
import org.gradle.initialization.BuildCancellationToken;
import org.gradle.initialization.BuildRequestMetaData;
import org.gradle.internal.buildevents.BuildStartedTime;
import org.gradle.internal.buildtree.BuildActionRunner;
import org.gradle.internal.concurrent.ExecutorFactory;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.execution.WorkInputListener;
import org.gradle.internal.execution.WorkInputListeners;
import org.gradle.internal.file.Stat;
import org.gradle.internal.invocation.BuildAction;
import org.gradle.internal.logging.text.StyledTextOutput;
import org.gradle.internal.logging.text.StyledTextOutputFactory;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.internal.session.BuildSessionActionExecutor;
import org.gradle.internal.session.BuildSessionContext;
import org.gradle.internal.snapshot.CaseSensitivity;
import org.gradle.internal.time.Clock;
import org.gradle.internal.watch.vfs.BuildLifecycleAwareVirtualFileSystem;
import org.gradle.internal.watch.vfs.FileChangeListeners;
import org.gradle.util.internal.DisconnectableInputStream;

import java.util.function.Supplier;

public class ContinuousBuildActionExecutor implements BuildSessionActionExecutor {
    private final BuildSessionActionExecutor delegate;
    private final WorkInputListeners inputsListeners;
    private final FileChangeListeners fileChangeListeners;
    private final BuildRequestMetaData requestMetaData;
    private final OperatingSystem operatingSystem;
    private final BuildCancellationToken cancellationToken;
    private final DeploymentRegistryInternal deploymentRegistry;
    private final ListenerManager listenerManager;
    private final BuildStartedTime buildStartedTime;
    private final Clock clock;
    private final Stat stat;
    private final CaseSensitivity caseSensitivity;
    private final BuildLifecycleAwareVirtualFileSystem virtualFileSystem;
    private final ExecutorFactory executorFactory;
    private final StyledTextOutput logger;

    public ContinuousBuildActionExecutor(
        WorkInputListeners inputListeners,
        FileChangeListeners fileChangeListeners,
        StyledTextOutputFactory styledTextOutputFactory,
        ExecutorFactory executorFactory,
        BuildRequestMetaData requestMetaData,
        BuildCancellationToken cancellationToken,
        DeploymentRegistryInternal deploymentRegistry,
        ListenerManager listenerManager,
        BuildStartedTime buildStartedTime,
        Clock clock,
        Stat stat,
        CaseSensitivity caseSensitivity,
        BuildLifecycleAwareVirtualFileSystem virtualFileSystem,
        BuildSessionActionExecutor delegate
    ) {
        this.inputsListeners = inputListeners;
        this.fileChangeListeners = fileChangeListeners;
        this.requestMetaData = requestMetaData;
        this.cancellationToken = cancellationToken;
        this.deploymentRegistry = deploymentRegistry;
        this.listenerManager = listenerManager;
        this.buildStartedTime = buildStartedTime;
        this.clock = clock;
        this.stat = stat;
        this.caseSensitivity = caseSensitivity;
        this.virtualFileSystem = virtualFileSystem;
        this.operatingSystem = OperatingSystem.current();
        this.executorFactory = executorFactory;
        this.logger = styledTextOutputFactory.create(ContinuousBuildActionExecutor.class, LogLevel.QUIET);
        this.delegate = delegate;
    }

    @Override
    public BuildActionRunner.Result execute(BuildAction action, BuildSessionContext buildSession) {
        if (action.getStartParameter().isContinuous()) {
            DefaultContinuousExecutionGate alwaysOpenExecutionGate = new DefaultContinuousExecutionGate();
            final CancellableOperationManager cancellableOperationManager = createCancellableOperationManager(requestMetaData, cancellationToken);
            return executeMultipleBuilds(action, requestMetaData, buildSession, cancellationToken, cancellableOperationManager, alwaysOpenExecutionGate);
        } else {
            try {
                return delegate.execute(action, buildSession);
            } finally {
                final CancellableOperationManager cancellableOperationManager = createCancellableOperationManager(requestMetaData, cancellationToken);
                waitForDeployments(action, requestMetaData, buildSession, cancellationToken, cancellableOperationManager);
            }
        }
    }

    private CancellableOperationManager createCancellableOperationManager(BuildRequestMetaData requestContext, BuildCancellationToken cancellationToken) {
        final CancellableOperationManager cancellableOperationManager;
        if (requestContext.isInteractive()) {
            if (!(System.in instanceof DisconnectableInputStream)) {
                System.setIn(new DisconnectableInputStream(System.in, executorFactory.create("continuous stdin")));
            }
            DisconnectableInputStream inputStream = (DisconnectableInputStream) System.in;
            cancellableOperationManager = new DefaultCancellableOperationManager(executorFactory.create("Cancel signal monitor"), inputStream, cancellationToken);
        } else {
            cancellableOperationManager = new PassThruCancellableOperationManager(cancellationToken);
        }
        return cancellableOperationManager;
    }

    private void waitForDeployments(
        BuildAction action,
        BuildRequestMetaData requestContext,
        BuildSessionContext buildSession,
        BuildCancellationToken cancellationToken,
        CancellableOperationManager cancellableOperationManager
    ) {
        if (!deploymentRegistry.getRunningDeployments().isEmpty()) {
            // Deployments are considered outOfDate until initial execution with file watching
            for (Deployment deployment : deploymentRegistry.getRunningDeployments()) {
                ((DeploymentInternal) deployment).outOfDate();
            }
            logger.println().println("Reloadable deployment detected. Entering continuous build.");
            resetBuildStartedTime();
            ContinuousExecutionGate deploymentRequestExecutionGate = deploymentRegistry.getExecutionGate();
            executeMultipleBuilds(action, requestContext, buildSession, cancellationToken, cancellableOperationManager, deploymentRequestExecutionGate);
        }
        cancellableOperationManager.closeInput();
    }

    private BuildActionRunner.Result executeMultipleBuilds(
        BuildAction action,
        BuildRequestMetaData requestContext,
        BuildSessionContext buildSession,
        BuildCancellationToken cancellationToken,
        CancellableOperationManager cancellableOperationManager,
        ContinuousExecutionGate continuousExecutionGate
    ) {
        BuildActionRunner.Result lastResult;
        PendingChangesListener pendingChangesListener = listenerManager.getBroadcaster(PendingChangesListener.class);
        while (true) {
            BuildInputHierarchy buildInputs = new BuildInputHierarchy(caseSensitivity, stat);
            ContinuousBuildTriggerHandler continuousBuildTriggerHandler = new ContinuousBuildTriggerHandler(
                cancellationToken,
                continuousExecutionGate,
                action.getStartParameter().getContinuousBuildQuietPeriod()
            );
            SingleFirePendingChangesListener singleFirePendingChangesListener = new SingleFirePendingChangesListener(pendingChangesListener);
            FileEventCollector fileEventCollector = new FileEventCollector(buildInputs, () -> {
                continuousBuildTriggerHandler.notifyFileChangeArrived();
                singleFirePendingChangesListener.onPendingChanges();
            });
            try {
                fileChangeListeners.addListener(fileEventCollector);
                lastResult = executeBuildAndAccumulateInputs(action, new AccumulateBuildInputsListener(buildInputs), buildSession);

                // Let the VFS clean itself up after the build
                virtualFileSystem.afterBuildFinished();

                if (buildInputs.isEmpty()) {
                    logger.println().withStyle(StyledTextOutput.Style.Failure).println("Exiting continuous build as Gradle did not detect any file system inputs.");
                    return lastResult;
                } else if (!continuousBuildTriggerHandler.hasBeenTriggered() && !virtualFileSystem.isWatchingAnyLocations()) {
                    logger.println().withStyle(StyledTextOutput.Style.Failure).println("Exiting continuous build as Gradle does not watch any file system locations.");
                    return lastResult;
                } else {
                    cancellableOperationManager.monitorInput(operationToken -> {
                        continuousBuildTriggerHandler.wait(
                            () -> logger.println().println("Waiting for changes to input files..." + determineExitHint(requestContext))
                        );
                        if (!operationToken.isCancellationRequested()) {
                            fileEventCollector.reportChanges(logger);
                        }
                    });
                }
            } finally {
                fileChangeListeners.removeListener(fileEventCollector);
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

    private BuildActionRunner.Result executeBuildAndAccumulateInputs(
        BuildAction action,
        WorkInputListener inputListener,
        BuildSessionContext buildSession
    ) {
        return withInputListener(
            inputListener,
            () -> delegate.execute(action, buildSession)
        );
    }

    private <T> T withInputListener(WorkInputListener listener, Supplier<T> supplier) {
        try {
            inputsListeners.addListener(listener);
            return supplier.get();
        } finally {
            inputsListeners.removeListener(listener);
        }
    }
}
