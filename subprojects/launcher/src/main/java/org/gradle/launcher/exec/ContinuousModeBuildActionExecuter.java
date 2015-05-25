/*
 * Copyright 2015 the original author or authors.
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

import org.gradle.api.Action;
import org.gradle.api.JavaVersion;
import org.gradle.api.execution.internal.TaskInputsListener;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.file.FileCollectionInternal;
import org.gradle.api.internal.file.FileSystemSubset;
import org.gradle.api.logging.LogLevel;
import org.gradle.execution.BasicCancellableOperationManager;
import org.gradle.execution.CancellableOperationManager;
import org.gradle.execution.DefaultCancellableOperationManager;
import org.gradle.initialization.BuildCancellationToken;
import org.gradle.initialization.BuildRequestContext;
import org.gradle.internal.concurrent.ExecutorFactory;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.filewatch.DefaultFileSystemChangeWaiter;
import org.gradle.internal.filewatch.FileSystemChangeWaiter;
import org.gradle.internal.filewatch.FileWatcherFactory;
import org.gradle.internal.invocation.BuildAction;
import org.gradle.logging.StyledTextOutput;
import org.gradle.logging.StyledTextOutputFactory;
import org.gradle.util.DisconnectableInputStream;
import org.gradle.util.SingleMessageLogger;

public class ContinuousModeBuildActionExecuter implements BuildExecuter {

    private final BuildActionExecuter<BuildActionParameters> delegate;
    private final ListenerManager listenerManager;
    private final FileSystemChangeWaiter waiter;
    private final ExecutorFactory executorFactory;
    private final JavaVersion javaVersion;
    private final StyledTextOutput logger;

    public ContinuousModeBuildActionExecuter(BuildActionExecuter<BuildActionParameters> delegate, FileWatcherFactory fileWatcherFactory, ListenerManager listenerManager, StyledTextOutputFactory styledTextOutputFactory, ExecutorFactory executorFactory) {
        this(delegate, listenerManager, styledTextOutputFactory, JavaVersion.current(), executorFactory, new DefaultFileSystemChangeWaiter(executorFactory, fileWatcherFactory));
    }

    ContinuousModeBuildActionExecuter(BuildActionExecuter<BuildActionParameters> delegate, ListenerManager listenerManager, StyledTextOutputFactory styledTextOutputFactory, JavaVersion javaVersion, ExecutorFactory executorFactory, FileSystemChangeWaiter waiter) {
        this.delegate = delegate;
        this.listenerManager = listenerManager;
        this.javaVersion = javaVersion;
        this.waiter = waiter;
        this.executorFactory = executorFactory;
        this.logger = styledTextOutputFactory.create(ContinuousModeBuildActionExecuter.class, LogLevel.LIFECYCLE);
    }

    @Override
    public Object execute(BuildAction action, BuildRequestContext requestContext, BuildActionParameters actionParameters) {
        if (actionParameters.isContinuousModeEnabled()) {
            return executeMultipleBuilds(action, requestContext, actionParameters);
        } else {
            return delegate.execute(action, requestContext, actionParameters);
        }
    }

    private Object executeMultipleBuilds(BuildAction action, BuildRequestContext requestContext, final BuildActionParameters actionParameters) {
        if (!javaVersion.isJava7Compatible()) {
            throw new IllegalStateException(String.format("Continuous building requires Java %s or later.", JavaVersion.VERSION_1_7));
        }
        SingleMessageLogger.incubatingFeatureUsed("Continuous building");

        BuildCancellationToken cancellationToken = requestContext.getCancellationToken();

        final CancellableOperationManager cancellableOperationManager;
        if (actionParameters.isInteractive()) {
            if (!(System.in instanceof DisconnectableInputStream)) {
                System.setIn(new DisconnectableInputStream(System.in));
            }
            DisconnectableInputStream inputStream = (DisconnectableInputStream) System.in;
            cancellableOperationManager = new DefaultCancellableOperationManager(executorFactory.create("cancel signal monitor"), inputStream, cancellationToken);
        } else {
            cancellableOperationManager = new BasicCancellableOperationManager(cancellationToken);
        }

        Object lastResult = null;
        int counter = 0;
        while (!cancellationToken.isCancellationRequested()) {
            if (++counter != 1) {
                // reset the time the build started so the total time makes sense
                requestContext.getBuildTimeClock().reset();
                logger.println("Change detected, executing build...").println();
            }

            FileSystemSubset.Builder fileSystemSubsetBuilder = FileSystemSubset.builder();
            try {
                lastResult = executeBuildAndAccumulateInputs(action, requestContext, actionParameters, fileSystemSubsetBuilder);
            } catch (ReportedException t) {
                lastResult = t;
            }

            final FileSystemSubset toWatch = fileSystemSubsetBuilder.build();
            if (toWatch.isEmpty()) {
                logger.println().withStyle(StyledTextOutput.Style.Failure).println("Exiting continuous building as no executed tasks declared file system inputs.");
                if (lastResult instanceof ReportedException) {
                    throw (ReportedException) lastResult;
                }
                return lastResult;
            } else {
                cancellableOperationManager.monitorInputExecute(new Action<BuildCancellationToken>() {
                    @Override
                    public void execute(BuildCancellationToken cancellationToken) {
                        waiter.wait(toWatch, cancellationToken, new Runnable() {
                            @Override
                            public void run() {
                                logger.println().println("Waiting for changes to input files of tasks..." + (actionParameters.isInteractive() ? " (ctrl+d to exit)" : ""));
                            }
                        });
                    }
                });
            }
        }

        logger.println("Build cancelled.");
        if (lastResult instanceof ReportedException) {
            throw (ReportedException) lastResult;
        }
        return lastResult;
    }

    private Object executeBuildAndAccumulateInputs(BuildAction action, BuildRequestContext requestContext, BuildActionParameters actionParameters, final FileSystemSubset.Builder fileSystemSubsetBuilder) {
        TaskInputsListener listener = new TaskInputsListener() {
            @Override
            public void onExecute(TaskInternal taskInternal, FileCollectionInternal fileSystemInputs) {
                fileSystemInputs.registerWatchPoints(fileSystemSubsetBuilder);
            }
        };
        listenerManager.addListener(listener);
        try {
            return delegate.execute(action, requestContext, actionParameters);
        } finally {
            listenerManager.removeListener(listener);
        }
    }

}
