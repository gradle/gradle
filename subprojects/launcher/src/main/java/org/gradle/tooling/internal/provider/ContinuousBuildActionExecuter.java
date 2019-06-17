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

import org.gradle.api.Action;
import org.gradle.api.execution.internal.TaskInputsListener;
import org.gradle.api.logging.LogLevel;
import org.gradle.deployment.internal.Deployment;
import org.gradle.deployment.internal.DeploymentInternal;
import org.gradle.deployment.internal.DeploymentRegistryInternal;
import org.gradle.execution.CancellableOperationManager;
import org.gradle.execution.DefaultCancellableOperationManager;
import org.gradle.execution.PassThruCancellableOperationManager;
import org.gradle.initialization.BuildCancellationToken;
import org.gradle.initialization.BuildRequestContext;
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
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.internal.time.Clock;
import org.gradle.launcher.exec.BuildActionExecuter;
import org.gradle.launcher.exec.BuildActionParameters;
import org.gradle.launcher.exec.BuildActionResult;
import org.gradle.util.DisconnectableInputStream;

public class ContinuousBuildActionExecuter implements BuildActionExecuter<BuildActionParameters> {
    private final BuildActionExecuter<BuildActionParameters> delegate;
    private final TaskInputsListener inputsListener;
    private final OperatingSystem operatingSystem;
    private final FileSystemChangeWaiterFactory changeWaiterFactory;
    private final ExecutorFactory executorFactory;
    private final StyledTextOutput logger;

    public ContinuousBuildActionExecuter(BuildActionExecuter<BuildActionParameters> delegate, FileSystemChangeWaiterFactory changeWaiterFactory, TaskInputsListener inputsListener, StyledTextOutputFactory styledTextOutputFactory, ExecutorFactory executorFactory) {
        this.delegate = delegate;
        this.inputsListener = inputsListener;
        this.operatingSystem = OperatingSystem.current();
        this.executorFactory = executorFactory;
        this.changeWaiterFactory = changeWaiterFactory;
        this.logger = styledTextOutputFactory.create(ContinuousBuildActionExecuter.class, LogLevel.QUIET);
    }

    @Override
    public BuildActionResult execute(BuildAction action, BuildRequestContext requestContext, final BuildActionParameters actionParameters, ServiceRegistry buildSessionScopeServices) {
        BuildCancellationToken cancellationToken = requestContext.getCancellationToken();
        if (actionParameters.isContinuous()) {
            DefaultContinuousExecutionGate alwaysOpenExecutionGate = new DefaultContinuousExecutionGate();
            final CancellableOperationManager cancellableOperationManager = createCancellableOperationManager(requestContext, cancellationToken);
            return executeMultipleBuilds(action, requestContext, actionParameters, buildSessionScopeServices, cancellableOperationManager, alwaysOpenExecutionGate);
        } else {
            try {
                return delegate.execute(action, requestContext, actionParameters, buildSessionScopeServices);
            } finally {
                final CancellableOperationManager cancellableOperationManager = createCancellableOperationManager(requestContext, cancellationToken);
                waitForDeployments(action, requestContext, actionParameters, buildSessionScopeServices, cancellableOperationManager);
            }
        }
    }

    private CancellableOperationManager createCancellableOperationManager(BuildRequestContext requestContext, BuildCancellationToken cancellationToken) {
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

    private void waitForDeployments(BuildAction action, BuildRequestContext requestContext, final BuildActionParameters actionParameters, ServiceRegistry buildSessionScopeServices, CancellableOperationManager cancellableOperationManager) {
        final DeploymentRegistryInternal deploymentRegistry = buildSessionScopeServices.get(DeploymentRegistryInternal.class);
        if (!deploymentRegistry.getRunningDeployments().isEmpty()) {
            // Deployments are considered outOfDate until initial execution with file watching
            for (Deployment deployment : deploymentRegistry.getRunningDeployments()) {
                ((DeploymentInternal) deployment).outOfDate();
            }
            logger.println().println("Reloadable deployment detected. Entering continuous build.");
            resetBuildStartedTime(buildSessionScopeServices);
            ContinuousExecutionGate deploymentRequestExecutionGate = deploymentRegistry.getExecutionGate();
            executeMultipleBuilds(action, requestContext, actionParameters, buildSessionScopeServices, cancellableOperationManager, deploymentRequestExecutionGate);
        }
        cancellableOperationManager.closeInput();
    }

    private BuildActionResult executeMultipleBuilds(BuildAction action, final BuildRequestContext requestContext, final BuildActionParameters actionParameters, final ServiceRegistry buildSessionScopeServices,
                                         CancellableOperationManager cancellableOperationManager, ContinuousExecutionGate continuousExecutionGate) {
        BuildCancellationToken cancellationToken = requestContext.getCancellationToken();

        BuildActionResult lastResult;
        while (true) {
            PendingChangesListener pendingChangesListener = buildSessionScopeServices.get(ListenerManager.class).getBroadcaster(PendingChangesListener.class);
            final FileSystemChangeWaiter waiter = changeWaiterFactory.createChangeWaiter(new SingleFirePendingChangesListener(pendingChangesListener), cancellationToken, continuousExecutionGate);
            try {
                lastResult = executeBuildAndAccumulateInputs(action, requestContext, actionParameters, waiter, buildSessionScopeServices);

                if (!waiter.isWatching()) {
                    logger.println().withStyle(StyledTextOutput.Style.Failure).println("Exiting continuous build as no executed tasks declared file system inputs.");
                    return lastResult;
                } else {
                    cancellableOperationManager.monitorInput(new Action<BuildCancellationToken>() {
                        @Override
                        public void execute(BuildCancellationToken cancellationToken) {
                            FileWatcherEventListener reporter = new DefaultFileWatcherEventListener();
                            waiter.wait(new Runnable() {
                                @Override
                                public void run() {
                                    logger.println().println("Waiting for changes to input files of tasks..." + determineExitHint(requestContext));
                                }
                            }, reporter);
                            if (!cancellationToken.isCancellationRequested()) {
                                reporter.reportChanges(logger);
                            }
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
                resetBuildStartedTime(buildSessionScopeServices);
            }
        }

        logger.println("Build cancelled.");
        return lastResult;
    }

    private void resetBuildStartedTime(ServiceRegistry buildSessionScopeServices) {
        BuildStartedTime buildStartedTime = buildSessionScopeServices.get(BuildStartedTime.class);
        Clock clock = buildSessionScopeServices.get(Clock.class);
        buildStartedTime.reset(clock.getCurrentTime());
    }

    private String determineExitHint(BuildRequestContext requestContext) {
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

    private BuildActionResult executeBuildAndAccumulateInputs(BuildAction action, BuildRequestContext requestContext, BuildActionParameters actionParameters, final FileSystemChangeWaiter waiter, ServiceRegistry buildSessionScopeServices) {
        try {
            inputsListener.setFileSystemWaiter(waiter);
            return delegate.execute(action, requestContext, actionParameters, buildSessionScopeServices);
        } finally {
            inputsListener.setFileSystemWaiter(null);
        }

    }
}
