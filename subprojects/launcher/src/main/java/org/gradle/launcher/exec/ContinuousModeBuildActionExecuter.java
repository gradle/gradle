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

import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.initialization.BuildRequestContext;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.filewatch.FileWatcherFactory;
import org.gradle.internal.invocation.BuildAction;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.launcher.continuous.BlockingTriggerable;
import org.gradle.launcher.continuous.TaskInputsWatcher;
import org.gradle.launcher.continuous.TriggerDetails;
import org.gradle.launcher.continuous.TriggerListener;
import org.gradle.util.SingleMessageLogger;

public class ContinuousModeBuildActionExecuter implements BuildActionExecuter<BuildActionParameters> {

    private final BuildActionExecuter<BuildActionParameters> delegate;
    private final Logger logger;
    private final BlockingTriggerable triggerable;
    private final ServiceRegistry services;

    public ContinuousModeBuildActionExecuter(BuildActionExecuter<BuildActionParameters> delegate, BlockingTriggerable triggerable, ServiceRegistry services) {
        this.delegate = delegate;
        this.triggerable = triggerable;
        this.services = services;
        this.logger = Logging.getLogger(ContinuousModeBuildActionExecuter.class);
    }

    @Override
    public Object execute(BuildAction action, BuildRequestContext requestContext, BuildActionParameters actionParameters) {
        if (continuousModeEnabled(actionParameters)) {
            SingleMessageLogger.incubatingFeatureUsed("Continuous mode");
            registerFileWatcherHooks();
            return executeMultipleBuilds(action, requestContext, actionParameters);
        }
        return executeSingleBuild(action, requestContext, actionParameters);
    }

    private void registerFileWatcherHooks() {
        ListenerManager listenerManager = services.get(ListenerManager.class);
        if (listenerManager != null) {
            FileWatcherFactory fileWatcherFactory = services.get(FileWatcherFactory.class);
            TaskInputsWatcher taskInputsWatcher = new TaskInputsWatcher(listenerManager.getBroadcaster(TriggerListener.class), fileWatcherFactory);
            listenerManager.addListener(taskInputsWatcher);
        }
    }

    private Object executeMultipleBuilds(BuildAction action, BuildRequestContext requestContext, BuildActionParameters actionParameters) {
        Object lastResult = null;
        while (buildNotStopped(requestContext)) {
            try {
                lastResult = executeSingleBuild(action, requestContext, actionParameters);
            } catch (Throwable t) {
                // TODO: logged already, are there certain cases we want to escape from this loop?
            }

            if (buildNotStopped(requestContext)) {
                logger.lifecycle("Waiting for a trigger. To exit 'continuous mode', use Ctrl+C.");
                TriggerDetails reason = triggerable.waitForTrigger();
                logger.lifecycle(reason.getType() + " triggered due to " + reason.getReason());
                if (reason.getType() == TriggerDetails.Type.REBUILD) {
                    // reset the time the build started so the total time makes sense
                    requestContext.getBuildTimeClock().reset();
                } else {
                    // stop building
                    break;
                }
            }
        }

        logger.lifecycle("Build cancelled, exiting 'continuous mode'.");
        return lastResult;
    }

    private Object executeSingleBuild(BuildAction action, BuildRequestContext requestContext, BuildActionParameters actionParameters) {
        return delegate.execute(action, requestContext, actionParameters);
    }

    private boolean continuousModeEnabled(BuildActionParameters actionParameters) {
        return actionParameters.isContinuousModeEnabled();
    }

    private boolean buildNotStopped(BuildRequestContext requestContext) {
        return !requestContext.getCancellationToken().isCancellationRequested();
    }

}
