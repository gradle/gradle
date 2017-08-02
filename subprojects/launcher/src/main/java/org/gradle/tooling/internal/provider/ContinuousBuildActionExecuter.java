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
import org.gradle.execution.CancellableOperationManager;
import org.gradle.execution.DefaultCancellableOperationManager;
import org.gradle.execution.PassThruCancellableOperationManager;
import org.gradle.initialization.BuildCancellationToken;
import org.gradle.initialization.BuildGateToken;
import org.gradle.initialization.BuildRequestContext;
import org.gradle.initialization.ReportedException;
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
import org.gradle.launcher.exec.BuildActionExecuter;
import org.gradle.launcher.exec.BuildActionParameters;
import org.gradle.util.DisconnectableInputStream;
import org.gradle.util.SingleMessageLogger;


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
    public Object execute(BuildAction action, BuildRequestContext requestContext, BuildActionParameters actionParameters, ServiceRegistry contextServices) {
        if (actionParameters.isContinuous()) {
            return executeMultipleBuilds(action, requestContext, actionParameters, contextServices);
        } else {
            return delegate.execute(action, requestContext, actionParameters, contextServices);
        }
    }

    private Object executeMultipleBuilds(BuildAction action, BuildRequestContext requestContext, final BuildActionParameters actionParameters, final ServiceRegistry buildSessionScopeServices) {
        SingleMessageLogger.incubatingFeatureUsed("Continuous build");

        BuildCancellationToken cancellationToken = requestContext.getCancellationToken();
        BuildGateToken buildGateToken = requestContext.getGateToken();

        final CancellableOperationManager cancellableOperationManager;
        if (actionParameters.isInteractive()) {
            if (!(System.in instanceof DisconnectableInputStream)) {
                System.setIn(new DisconnectableInputStream(System.in));
            }
            DisconnectableInputStream inputStream = (DisconnectableInputStream) System.in;
            cancellableOperationManager = new DefaultCancellableOperationManager(executorFactory.create("cancel signal monitor"), inputStream, cancellationToken);
        } else {
            cancellableOperationManager = new PassThruCancellableOperationManager(cancellationToken);
        }

        Object lastResult = null;
        int counter = 0;
        while (!cancellationToken.isCancellationRequested()) {
            if (++counter != 1) {
                // reset the time the build started so the total time makes sense
                requestContext.getBuildTimeClock().reset();
                logger.println("Change detected, executing build...").println();
            }

            PendingChangesListener pendingChangesListener = buildSessionScopeServices.get(ListenerManager.class).getBroadcaster(PendingChangesListener.class);
            final FileSystemChangeWaiter waiter = changeWaiterFactory.createChangeWaiter(new SingleFirePendingChangesListener(pendingChangesListener), cancellationToken, buildGateToken);
            try {
                try {
                    lastResult = executeBuildAndAccumulateInputs(action, requestContext, actionParameters, waiter, buildSessionScopeServices);
                } catch (ReportedException t) {
                    lastResult = t;
                }

                if (!waiter.isWatching()) {
                    logger.println().withStyle(StyledTextOutput.Style.Failure).println("Exiting continuous build as no executed tasks declared file system inputs.");
                    if (lastResult instanceof ReportedException) {
                        throw (ReportedException) lastResult;
                    }
                    return lastResult;
                } else {
                    cancellableOperationManager.monitorInput(new Action<BuildCancellationToken>() {
                        @Override
                        public void execute(BuildCancellationToken cancellationToken) {
                            FileWatcherEventListener reporter = new DefaultFileWatcherEventListener();
                            waiter.wait(new Runnable() {
                                @Override
                                public void run() {
                                    logger.println().println("Waiting for changes to input files of tasks..." + determineExitHint(actionParameters));
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
        }

        logger.println("Build cancelled.");
        if (lastResult instanceof ReportedException) {
            throw (ReportedException) lastResult;
        }
        return lastResult;
    }

    public String determineExitHint(BuildActionParameters actionParameters) {
        if (actionParameters.isInteractive()) {
            if (operatingSystem.isWindows()) {
                return " (ctrl-d then enter to exit)";
            } else {
                return " (ctrl-d to exit)";
            }
        } else {
            return "";
        }
    }

    private Object executeBuildAndAccumulateInputs(BuildAction action, BuildRequestContext requestContext, BuildActionParameters actionParameters, final FileSystemChangeWaiter waiter, ServiceRegistry buildSessionScopeServices) {
        try {
            inputsListener.setFileSystemWaiter(waiter);
            return delegate.execute(action, requestContext, actionParameters, buildSessionScopeServices);
        } finally {
            inputsListener.setFileSystemWaiter(null);
        }

    }
}
