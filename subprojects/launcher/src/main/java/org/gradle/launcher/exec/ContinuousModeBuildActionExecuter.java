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

import org.gradle.api.execution.internal.TaskInputsListener;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.file.FileCollectionInternal;
import org.gradle.api.internal.file.FileSystemSubset;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.initialization.BuildRequestContext;
import org.gradle.internal.BiAction;
import org.gradle.internal.concurrent.ExecutorFactory;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.filewatch.FileSystemChangeWaiter;
import org.gradle.internal.filewatch.FileWatcherFactory;
import org.gradle.internal.invocation.BuildAction;
import org.gradle.util.SingleMessageLogger;

import java.util.concurrent.atomic.AtomicBoolean;

public class ContinuousModeBuildActionExecuter implements BuildExecuter {

    private final BuildActionExecuter<BuildActionParameters> delegate;
    private final ListenerManager listenerManager;
    private final BiAction<? super FileSystemSubset, ? super Runnable> waiter;
    private final AtomicBoolean keyboardCancellationRequested;

    private final Logger logger;

    public ContinuousModeBuildActionExecuter(BuildActionExecuter<BuildActionParameters> delegate, FileWatcherFactory fileWatcherFactory, ListenerManager listenerManager, ExecutorFactory executorFactory) {
        this(delegate, listenerManager, new FileSystemChangeWaiter(executorFactory, fileWatcherFactory));
    }

    ContinuousModeBuildActionExecuter(BuildActionExecuter<BuildActionParameters> delegate, ListenerManager listenerManager, BiAction<? super FileSystemSubset, ? super Runnable> waiter) {
        this.delegate = delegate;
        this.listenerManager = listenerManager;
        this.waiter = waiter;
        this.keyboardCancellationRequested = (waiter instanceof FileSystemChangeWaiter) ? ((FileSystemChangeWaiter) waiter).getCancellationRequested() : new AtomicBoolean(false);
        this.logger = Logging.getLogger(ContinuousModeBuildActionExecuter.class);
    }

    @Override
    public Object execute(BuildAction action, BuildRequestContext requestContext, BuildActionParameters actionParameters) {
        if (continuousModeEnabled(actionParameters)) {
            SingleMessageLogger.incubatingFeatureUsed("Continuous mode");
            return executeMultipleBuilds(action, requestContext, actionParameters);
        }
        return executeSingleBuild(action, requestContext, actionParameters);
    }

    private Object executeMultipleBuilds(BuildAction action, BuildRequestContext requestContext, BuildActionParameters actionParameters) {
        Object lastResult = null;
        int counter = 0;
        while (buildNotStopped(requestContext)) {
            if (++counter != 1) {
                // reset the time the build started so the total time makes sense
                requestContext.getBuildTimeClock().reset();
            }

            FileSystemSubset.Builder fileSystemSubsetBuilder = FileSystemSubset.builder();
            try {
                lastResult = executeBuildAndAccumulateInputs(action, requestContext, actionParameters, fileSystemSubsetBuilder);
            } catch (Throwable t) {
                // TODO: logged already, are there certain cases we want to escape from this loop?
            }

            FileSystemSubset toWatch = fileSystemSubsetBuilder.build();
            if (toWatch.isEmpty()) {
                logger.lifecycle("Exiting continuous mode as no executed tasks declared file system inputs.");
                return lastResult;
            } else if (buildNotStopped(requestContext)) {
                waiter.execute(toWatch, new Runnable() {
                    @Override
                    public void run() {
                        logger.lifecycle("Waiting for a trigger. To exit 'continuous mode', use Ctrl+D.");
                    }
                });
            }
        }

        logger.lifecycle("Build cancelled, exiting 'continuous mode'.");
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
            return executeSingleBuild(action, requestContext, actionParameters);
        } finally {
            listenerManager.removeListener(listener);
        }
    }

    private Object executeSingleBuild(BuildAction action, BuildRequestContext requestContext, BuildActionParameters actionParameters) {
        return delegate.execute(action, requestContext, actionParameters);
    }

    private boolean continuousModeEnabled(BuildActionParameters actionParameters) {
        return actionParameters.isContinuousModeEnabled();
    }

    private boolean buildNotStopped(BuildRequestContext requestContext) {
        return !requestContext.getCancellationToken().isCancellationRequested() && !keyboardCancellationRequested.get();
    }

}
