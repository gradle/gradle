/*
 * Copyright 2011 the original author or authors.
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

package org.gradle.tooling.internal.consumer;

import org.gradle.StartParameter;
import org.gradle.api.internal.project.DefaultServiceRegistry;
import org.gradle.api.internal.project.ServiceRegistry;
import org.gradle.api.internal.project.SynchronizedServiceRegistry;
import org.gradle.listener.DefaultListenerManager;
import org.gradle.listener.ListenerManager;
import org.gradle.logging.internal.DefaultProgressLoggerFactory;
import org.gradle.logging.internal.ProgressListener;
import org.gradle.tooling.internal.consumer.loader.CachingToolingImplementationLoader;
import org.gradle.tooling.internal.consumer.loader.DefaultToolingImplementationLoader;
import org.gradle.tooling.internal.consumer.loader.SynchronizedToolingImplementationLoader;
import org.gradle.tooling.internal.consumer.loader.ToolingImplementationLoader;
import org.gradle.util.TrueTimeProvider;

/**
 * by Szczepan Faber, created at: 12/6/11
 */
public class ConnectorServices {

    private static ServiceRegistry SINGLETON_REGISTRY = new SynchronizedServiceRegistry(new ConnectorServiceRegistry());

    public DefaultGradleConnector createConnector() {
        ListenerManager listenerManager = new DefaultListenerManager();
        DefaultProgressLoggerFactory progressLoggerFactory = new DefaultProgressLoggerFactory(listenerManager.getBroadcaster(ProgressListener.class), new TrueTimeProvider());
        ConnectionFactory connectionFactory = new ConnectionFactory(SINGLETON_REGISTRY.get(ToolingImplementationLoader.class), listenerManager, progressLoggerFactory);
        return new DefaultGradleConnector(connectionFactory, new DistributionFactory(StartParameter.DEFAULT_GRADLE_USER_HOME, progressLoggerFactory));
    }

    /**
     * Resets the state of connector services. Meant to be used only for testing!
     */
    public void reset() {
        SINGLETON_REGISTRY = new SynchronizedServiceRegistry(new ConnectorServiceRegistry());
    }

    private static class ConnectorServiceRegistry extends DefaultServiceRegistry {
        protected ToolingImplementationLoader createToolingImplementationLoader() {
            return new SynchronizedToolingImplementationLoader(new CachingToolingImplementationLoader(new DefaultToolingImplementationLoader()));
        }
    }
}
