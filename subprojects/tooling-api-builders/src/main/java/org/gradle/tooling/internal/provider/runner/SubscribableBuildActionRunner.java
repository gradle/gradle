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
import org.gradle.internal.invocation.BuildAction;
import org.gradle.internal.invocation.BuildActionRunner;
import org.gradle.internal.invocation.BuildController;
import org.gradle.tooling.internal.provider.SubscribableBuildAction;

public class SubscribableBuildActionRunner implements BuildActionRunner {
    private BuildClientSubscriptionHandler buildClientSubscriptionHandler;
    private BuildActionRunner delegate;

    public SubscribableBuildActionRunner(BuildClientSubscriptionHandler buildClientSubscriptionHandler, BuildActionRunner delegate) {
        this.buildClientSubscriptionHandler = buildClientSubscriptionHandler;
        this.delegate = delegate;
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
        if (!buildClientSubscriptionHandler.hasClientSubscriptionsRegistered(subscribableBuildAction.getClientSubscriptions())) {
            buildClientSubscriptionHandler.registerListenersForClientSubscriptions(subscribableBuildAction.getClientSubscriptions(), gradle);
        }
        delegate.run(action, buildController);
        if (buildController.hasResult()) {
            buildClientSubscriptionHandler.removeClientSubscriptions(subscribableBuildAction.getClientSubscriptions());
        }
    }
}
