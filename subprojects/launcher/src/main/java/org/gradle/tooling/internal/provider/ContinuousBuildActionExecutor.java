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

import com.google.common.collect.ImmutableMap;
import org.gradle.api.execution.internal.FileChangeListener;
import org.gradle.api.execution.internal.TaskInputsListener;
import org.gradle.api.execution.internal.TaskInputsListeners;
import org.gradle.api.internal.TaskInternal;
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
import org.gradle.internal.UncheckedException;
import org.gradle.internal.buildevents.BuildStartedTime;
import org.gradle.internal.buildtree.BuildActionRunner;
import org.gradle.internal.concurrent.ExecutorFactory;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.file.FileHierarchySet;
import org.gradle.internal.filewatch.DefaultFileWatcherEventListener;
import org.gradle.internal.filewatch.FileWatcherEvent;
import org.gradle.internal.filewatch.FileWatcherEventListener;
import org.gradle.internal.filewatch.PendingChangesListener;
import org.gradle.internal.filewatch.SingleFirePendingChangesListener;
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint;
import org.gradle.internal.invocation.BuildAction;
import org.gradle.internal.logging.text.StyledTextOutput;
import org.gradle.internal.logging.text.StyledTextOutputFactory;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.internal.session.BuildSessionActionExecutor;
import org.gradle.internal.session.BuildSessionContext;
import org.gradle.internal.time.Clock;
import org.gradle.internal.watch.registry.FileWatcherRegistry;
import org.gradle.internal.watch.registry.impl.Combiners;
import org.gradle.internal.watch.vfs.BuildLifecycleAwareVirtualFileSystem;
import org.gradle.util.internal.DisconnectableInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Path;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Supplier;

public class ContinuousBuildActionExecutor implements BuildSessionActionExecutor {
    private final BuildSessionActionExecutor delegate;
    private final TaskInputsListeners inputsListeners;
    private final BuildRequestMetaData requestMetaData;
    private final OperatingSystem operatingSystem;
    private final BuildCancellationToken cancellationToken;
    private final DeploymentRegistryInternal deploymentRegistry;
    private final ListenerManager listenerManager;
    private final BuildStartedTime buildStartedTime;
    private final Clock clock;
    private final BuildLifecycleAwareVirtualFileSystem virtualFileSystem;
    private final ExecutorFactory executorFactory;
    private final StyledTextOutput logger;

    public ContinuousBuildActionExecutor(
        TaskInputsListeners inputsListeners,
        StyledTextOutputFactory styledTextOutputFactory,
        ExecutorFactory executorFactory,
        BuildRequestMetaData requestMetaData,
        BuildCancellationToken cancellationToken,
        DeploymentRegistryInternal deploymentRegistry,
        ListenerManager listenerManager,
        BuildStartedTime buildStartedTime,
        Clock clock,
        BuildLifecycleAwareVirtualFileSystem virtualFileSystem,
        BuildSessionActionExecutor delegate
    ) {
        this.inputsListeners = inputsListeners;
        this.requestMetaData = requestMetaData;
        this.cancellationToken = cancellationToken;
        this.deploymentRegistry = deploymentRegistry;
        this.listenerManager = listenerManager;
        this.buildStartedTime = buildStartedTime;
        this.clock = clock;
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
                System.setIn(new DisconnectableInputStream(System.in));
            }
            DisconnectableInputStream inputStream = (DisconnectableInputStream) System.in;
            cancellableOperationManager = new DefaultCancellableOperationManager(executorFactory.create("Cancel signal monitor"), inputStream, cancellationToken);
        } else {
            cancellableOperationManager = new PassThruCancellableOperationManager(cancellationToken);
        }
        return cancellableOperationManager;
    }

    private void waitForDeployments(BuildAction action, BuildRequestMetaData requestContext, BuildSessionContext buildSession, BuildCancellationToken cancellationToken, CancellableOperationManager cancellableOperationManager) {
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

    private BuildActionRunner.Result executeMultipleBuilds(BuildAction action, BuildRequestMetaData requestContext, BuildSessionContext buildSession,
                                                           BuildCancellationToken cancellationToken, CancellableOperationManager cancellableOperationManager, ContinuousExecutionGate continuousExecutionGate) {
        BuildActionRunner.Result lastResult;
        while (true) {
            PendingChangesListener pendingChangesListener = listenerManager.getBroadcaster(PendingChangesListener.class);
            virtualFileSystem.registerChangeBroadcaster(listenerManager.getBroadcaster(FileChangeListener.class));
            FileSystemChangeListener fileSystemChangeListener = new FileSystemChangeListener(new SingleFirePendingChangesListener(pendingChangesListener), cancellationToken, continuousExecutionGate);
            try {
                listenerManager.addListener(fileSystemChangeListener);
                lastResult = executeBuildAndAccumulateInputs(action, fileSystemChangeListener, buildSession);

                if (!fileSystemChangeListener.hasAnyInputs()) {
                    logger.println().withStyle(StyledTextOutput.Style.Failure).println("Exiting continuous build as no executed tasks declared file system inputs.");
                    return lastResult;
                } else {
                    cancellableOperationManager.monitorInput(operationToken -> {
                        FileWatcherEventListener reporter = new DefaultFileWatcherEventListener();
                        fileSystemChangeListener.wait(
                            () -> logger.println().println("Waiting for changes to input files of tasks..." + determineExitHint(requestContext)),
                            reporter
                        );
                        if (!operationToken.isCancellationRequested()) {
                            reporter.reportChanges(logger);
                        }
                    });
                }
            } finally {
                listenerManager.removeListener(fileSystemChangeListener);
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

    public static class FileSystemChangeListener implements FileChangeListener, TaskInputsListener {
        private static final Logger LOGGER = LoggerFactory.getLogger(FileSystemChangeListener.class);

        private final PendingChangesListener pendingChangesListener;
        private final BuildCancellationToken cancellationToken;
        private final ContinuousExecutionGate continuousExecutionGate;
        private final BlockingQueue<String> pendingChanges = new LinkedBlockingQueue<>(1);
        private volatile FileHierarchySet inputs = FileHierarchySet.empty();

        public FileSystemChangeListener(PendingChangesListener pendingChangesListener, BuildCancellationToken cancellationToken, ContinuousExecutionGate continuousExecutionGate) {
            this.pendingChangesListener = pendingChangesListener;
            this.cancellationToken = cancellationToken;
            this.continuousExecutionGate = continuousExecutionGate;
        }

        public boolean hasAnyInputs() {
            return inputs != FileHierarchySet.empty();
        }

        void wait(Runnable notifier, FileWatcherEventListener eventListener) {
            Runnable cancellationHandler = () -> pendingChanges.offer("Build cancelled");
            if (cancellationToken.isCancellationRequested()) {
                return;
            }
            try {
                cancellationToken.addCallback(cancellationHandler);
                String pendingChange = pendingChanges.take();
                LOGGER.info("Received pending change: {}", pendingChange);
                eventListener.onChange(FileWatcherEvent.modify(new File(pendingChange)));
                notifier.run();
                if (!cancellationToken.isCancellationRequested()) {
                    continuousExecutionGate.waitForOpen();
                }
            } catch (InterruptedException e) {
                throw UncheckedException.throwAsUncheckedException(e);
            } finally {
                cancellationToken.removeCallback(cancellationHandler);
            }
        }

        @Override
        public void handleChange(FileWatcherRegistry.Type type, Path path) {
            String absolutePath = path.toString();
            if (inputs.contains(absolutePath)) {
                // got a change, store it
                if (pendingChanges.offer(absolutePath)) {
                    pendingChangesListener.onPendingChanges();
                }
            }
        }

        @Override
        public void stopWatchingAfterError() {
            if (pendingChanges.offer("Error watching files")) {
                pendingChangesListener.onPendingChanges();
            }
        }

        @Override
        public synchronized void onExecute(TaskInternal task, ImmutableMap<String, CurrentFileCollectionFingerprint> fingerprints) {
            this.inputs = fingerprints.values().stream()
                .flatMap(fingerprint -> fingerprint.getRootHashes().keySet().stream())
                .reduce(inputs, FileHierarchySet::plus, Combiners.nonCombining());
        }
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
        FileSystemChangeListener changeListener,
        BuildSessionContext buildSession
    ) {
        return withTaskInputsListener(
            changeListener,
            () -> delegate.execute(action, buildSession)
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
