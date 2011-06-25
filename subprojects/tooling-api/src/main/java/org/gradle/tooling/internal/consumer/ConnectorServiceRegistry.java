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
import org.gradle.listener.DefaultListenerManager;
import org.gradle.listener.ListenerManager;
import org.gradle.logging.ProgressLoggerFactory;
import org.gradle.logging.internal.DefaultProgressLoggerFactory;
import org.gradle.logging.internal.ProgressListener;
import org.gradle.util.TrueTimeProvider;

public class ConnectorServiceRegistry extends DefaultServiceRegistry {
    protected ListenerManager createListenerManager() {
        return new DefaultListenerManager();
    }

    protected ProgressLoggerFactory createProgressLoggerFactory() {
        return new DefaultProgressLoggerFactory(get(ListenerManager.class).getBroadcaster(ProgressListener.class), new TrueTimeProvider());
    }

    protected ToolingImplementationLoader createToolingImplementationLoader() {
        return new CachingToolingImplementationLoader(new DefaultToolingImplementationLoader());
    }

    protected ConnectionFactory createConnectionFactory() {
        return new ConnectionFactory(get(ToolingImplementationLoader.class), get(ListenerManager.class), get(ProgressLoggerFactory.class));
    }

    protected DistributionFactory createDistributionFactory() {
        return new DistributionFactory(StartParameter.DEFAULT_GRADLE_USER_HOME, get(ProgressLoggerFactory.class));
    }
}
