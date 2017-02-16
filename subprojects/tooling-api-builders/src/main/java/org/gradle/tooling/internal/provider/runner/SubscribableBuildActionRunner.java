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

package org.gradle.tooling.internal.provider.runner;

import org.gradle.api.internal.GradleInternal;
import org.gradle.initialization.BuildEventConsumer;
import org.gradle.internal.invocation.BuildAction;
import org.gradle.internal.invocation.BuildActionRunner;
import org.gradle.internal.invocation.BuildController;
import org.gradle.internal.progress.BuildOperationInternal;
import org.gradle.internal.progress.BuildOperationListener;
import org.gradle.internal.progress.BuildOperationService;
import org.gradle.internal.progress.OperationResult;
import org.gradle.internal.progress.OperationStartEvent;
import org.gradle.tooling.internal.provider.BuildClientSubscriptions;
import org.gradle.tooling.internal.provider.SubscribableBuildAction;

import java.util.ArrayList;
import java.util.List;

public class SubscribableBuildActionRunner implements BuildActionRunner {
    private static final BuildOperationListener NO_OP = new BuildOperationListener() {
        @Override
        public void started(BuildOperationInternal buildOperation, OperationStartEvent startEvent) {
        }

        @Override
        public void finished(BuildOperationInternal buildOperation, OperationResult finishEvent) {
        }
    };
    private final BuildActionRunner delegate;
    private final BuildOperationService buildOperationService;
    private final List<BuildOperationListener> listeners = new ArrayList<BuildOperationListener>();

    public SubscribableBuildActionRunner(BuildActionRunner delegate, BuildOperationService buildOperationService) {
        this.delegate = delegate;
        this.buildOperationService = buildOperationService;
    }

    private void registerListenersForClientSubscriptions(BuildClientSubscriptions clientSubscriptions, GradleInternal gradle) {
        BuildEventConsumer eventConsumer = gradle.getServices().get(BuildEventConsumer.class);
        if (clientSubscriptions.isSendTestProgressEvents()) {
            registerListener(new ClientForwardingTestOperationListener(eventConsumer, clientSubscriptions));
        }
        if (!clientSubscriptions.isSendBuildProgressEvents() && !clientSubscriptions.isSendTaskProgressEvents()) {
            return;
        }

        BuildOperationListener buildListener = NO_OP;
        if (clientSubscriptions.isSendBuildProgressEvents()) {
            buildListener = new ClientForwardingBuildOperationListener(eventConsumer);
        }
        buildListener = new ClientForwardingTaskOperationListener(eventConsumer, clientSubscriptions, buildListener);
        registerListener(buildListener);
    }

    private void registerListener(BuildOperationListener listener) {
        listeners.add(listener);
        buildOperationService.addListener(listener);
    }

    @Override
    public void run(BuildAction action, BuildController buildController) {
        if (!(action instanceof SubscribableBuildAction)) {
            return;
        }
        GradleInternal gradle = buildController.getGradle();
        SubscribableBuildAction subscribableBuildAction = (SubscribableBuildAction) action;

        // register listeners that dispatch all progress via the registered BuildEventConsumer instance,
        // this allows to send progress events back to the DaemonClient (via short-cut)
        registerListenersForClientSubscriptions(subscribableBuildAction.getClientSubscriptions(), gradle);
        try {
            delegate.run(action, buildController);
        }finally {
            for (BuildOperationListener listener : listeners) {
                buildOperationService.removeListener(listener);
            }
        }

    }
}
