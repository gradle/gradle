/*
 * Copyright 2017 the original author or authors.
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

import org.gradle.api.internal.GradleInternal;
import org.gradle.initialization.BuildEventConsumer;
import org.gradle.internal.Factory;
import org.gradle.internal.invocation.BuildAction;
import org.gradle.internal.invocation.BuildActionRunner;
import org.gradle.internal.invocation.BuildController;
import org.gradle.internal.progress.BuildOperationListener;
import org.gradle.internal.progress.BuildOperationService;

import java.util.ArrayList;
import java.util.List;

public class SubscribableBuildActionRunner implements BuildActionRunner {
    private final BuildActionRunner delegate;
    private final BuildOperationService buildOperationService;
    private final List<BuildOperationListener> listeners = new ArrayList<BuildOperationListener>();
    private final Factory<List<? extends SubscribableBuildActionRunnerRegistration>> registrationsFactory;

    public SubscribableBuildActionRunner(BuildActionRunner delegate, BuildOperationService buildOperationService, Factory<List<? extends SubscribableBuildActionRunnerRegistration>> registrationsFactory) {
        this.delegate = delegate;
        this.buildOperationService = buildOperationService;
        this.registrationsFactory = registrationsFactory;
    }

    @Override
    public void run(BuildAction action, BuildController buildController) {
        boolean subscribable = action instanceof SubscribableBuildAction;
        if (subscribable) {
            GradleInternal gradle = buildController.getGradle();
            SubscribableBuildAction subscribableBuildAction = (SubscribableBuildAction) action;
            // register listeners that dispatch all progress via the registered BuildEventConsumer instance,
            // this allows to send progress events back to the DaemonClient (via short-cut)
            registerListenersForClientSubscriptions(subscribableBuildAction.getClientSubscriptions(), gradle);
        }
        try {
            delegate.run(action, buildController);
        } finally {
            for (BuildOperationListener listener : listeners) {
                buildOperationService.removeListener(listener);
            }
        }
    }

    private void registerListenersForClientSubscriptions(BuildClientSubscriptions clientSubscriptions, GradleInternal gradle) {
        BuildEventConsumer eventConsumer = gradle.getServices().get(BuildEventConsumer.class);
        for (SubscribableBuildActionRunnerRegistration registration : registrationsFactory.create()) {
            for (BuildOperationListener listener : registration.createListeners(clientSubscriptions, eventConsumer)) {
                registerListener(listener);
            }
        }
    }

    private void registerListener(BuildOperationListener listener) {
        listeners.add(listener);
        buildOperationService.addListener(listener);
    }
}
