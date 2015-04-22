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
import org.gradle.internal.invocation.BuildAction;
import org.gradle.launcher.continuous.BlockingTriggerListener;
import org.gradle.launcher.continuous.TriggerDetails;
import org.gradle.launcher.continuous.TriggerGenerator;
import org.gradle.launcher.continuous.TriggerGeneratorFactory;
import org.gradle.util.SingleMessageLogger;

public class ContinuousModeBuildActionExecuter implements BuildActionExecuter<BuildActionParameters> {

    private final BuildActionExecuter<BuildActionParameters> delegate;
    private final TriggerGeneratorFactory triggerGeneratorFactory;
    private final Logger logger;
    private final BlockingTriggerListener triggerListener;

    public ContinuousModeBuildActionExecuter(BuildActionExecuter<BuildActionParameters> delegate, TriggerGeneratorFactory triggerGeneratorFactory, BlockingTriggerListener triggerListener) {
        this.delegate = delegate;
        this.triggerGeneratorFactory = triggerGeneratorFactory;
        this.triggerListener = triggerListener;
        this.logger = Logging.getLogger(ContinuousModeBuildActionExecuter.class);
    }

    @Override
    public Object execute(BuildAction action, BuildRequestContext requestContext, BuildActionParameters actionParameters) {
        if (continuousModeEnabled(actionParameters)) {
            SingleMessageLogger.incubatingFeatureUsed("Continuous mode");
            // TODO: Put this somewhere else?
            TriggerGenerator generator = triggerGeneratorFactory.newInstance();
            generator.start();
            try {
                return executeMultipleBuilds(action, requestContext, actionParameters);
            } finally {
                generator.stop();
            }
        }
        return executeSingleBuild(action, requestContext, actionParameters);
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
                TriggerDetails reason = triggerListener.waitForTrigger();
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
