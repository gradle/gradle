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

import org.gradle.initialization.BuildEventConsumer;
import org.gradle.internal.build.event.BuildEventListenerFactory;
import org.gradle.internal.build.event.BuildEventSubscriptions;
import org.gradle.internal.buildtree.BuildActionRunner;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.invocation.BuildAction;
import org.gradle.internal.operations.BuildOperationListener;
import org.gradle.internal.operations.BuildOperationListenerManager;
import org.gradle.internal.session.BuildSessionActionExecutor;
import org.gradle.internal.session.BuildSessionContext;
import org.gradle.tooling.internal.provider.action.SubscribableBuildAction;

import java.util.ArrayList;
import java.util.List;

/**
 * Attaches build operation listeners to forward relevant operations back to the client.
 */
public class SubscribableBuildActionExecutor implements BuildSessionActionExecutor {
    private final BuildEventConsumer eventConsumer;
    private final BuildSessionActionExecutor delegate;
    private final ListenerManager listenerManager;
    private final BuildOperationListenerManager buildOperationListenerManager;
    private final List<Object> listeners = new ArrayList<>();
    private final BuildEventListenerFactory factory;

    public SubscribableBuildActionExecutor(ListenerManager listenerManager,
                                           BuildOperationListenerManager buildOperationListenerManager,
                                           BuildEventListenerFactory factory,
                                           BuildEventConsumer eventConsumer,
                                           BuildSessionActionExecutor delegate) {
        this.listenerManager = listenerManager;
        this.buildOperationListenerManager = buildOperationListenerManager;
        this.factory = factory;
        this.eventConsumer = eventConsumer;
        this.delegate = delegate;
    }

    @Override
    public BuildActionRunner.Result execute(BuildAction action, BuildSessionContext buildSession) {
        if (action instanceof SubscribableBuildAction) {
            SubscribableBuildAction subscribableBuildAction = (SubscribableBuildAction) action;
            registerListenersForClientSubscriptions(subscribableBuildAction.getClientSubscriptions(), eventConsumer);
        }
        try {
            return delegate.execute(action, buildSession);
        } finally {
            for (Object listener : listeners) {
                listenerManager.removeListener(listener);
                if (listener instanceof BuildOperationListener) {
                    buildOperationListenerManager.removeListener((BuildOperationListener) listener);
                }
            }
            listeners.clear();
        }
    }

    private void registerListenersForClientSubscriptions(BuildEventSubscriptions clientSubscriptions, BuildEventConsumer eventConsumer) {
        for (Object listener : factory.createListeners(clientSubscriptions, eventConsumer)) {
            registerListener(listener);
        }
    }

    private void registerListener(Object listener) {
        listeners.add(listener);
        listenerManager.addListener(listener);
        if (listener instanceof BuildOperationListener) {
            buildOperationListenerManager.addListener((BuildOperationListener) listener);
        }
    }
}
