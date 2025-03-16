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

import org.gradle.internal.Factory;
import org.gradle.internal.concurrent.DefaultExecutorFactory;
import org.gradle.internal.concurrent.ExecutorFactory;
import org.gradle.internal.operations.BuildOperationIdFactory;
import org.gradle.internal.operations.DefaultBuildOperationIdFactory;
import org.gradle.internal.service.CloseableServiceRegistry;
import org.gradle.internal.service.Provides;
import org.gradle.internal.service.ServiceRegistrationProvider;
import org.gradle.internal.service.ServiceRegistryBuilder;
import org.gradle.internal.time.Clock;
import org.gradle.internal.time.Time;
import org.gradle.tooling.CancellationTokenSource;
import org.gradle.tooling.internal.consumer.loader.CachingToolingImplementationLoader;
import org.gradle.tooling.internal.consumer.loader.DefaultToolingImplementationLoader;
import org.gradle.tooling.internal.consumer.loader.SynchronizedToolingImplementationLoader;
import org.gradle.tooling.internal.consumer.loader.ToolingImplementationLoader;

public class ConnectorServices {
    private static CloseableServiceRegistry singletonRegistry;

    static {
        singletonRegistry = ConnectorServiceRegistry.create();
    }

    public static DefaultGradleConnector createConnector() {
        return singletonRegistry.getFactory(DefaultGradleConnector.class).create();
    }

    public static CancellationTokenSource createCancellationTokenSource() {
        return new DefaultCancellationTokenSource();
    }

    public static void close() {
        singletonRegistry.close();
    }

    /**
     * Resets the state of connector services. Meant to be used only for testing!
     */
    public static void reset() {
        singletonRegistry.close();
        singletonRegistry = ConnectorServiceRegistry.create();
    }

    private static class ConnectorServiceRegistry implements ServiceRegistrationProvider {

        // Note: if the class or the method changes, this has to be adjusted in `ToolingApi.createClientConnectorServiceRegistry()` fixture
        private static CloseableServiceRegistry create() {
            return ServiceRegistryBuilder.builder()
                .displayName("connector services")
                .provider(new ConnectorServiceRegistry())
                .build();
        }

        @Provides
        protected Factory<DefaultGradleConnector> createConnectorFactory(final ConnectionFactory connectionFactory, final DistributionFactory distributionFactory) {
            return new Factory<DefaultGradleConnector>() {
                @Override
                public DefaultGradleConnector create() {
                    return new DefaultGradleConnector(connectionFactory, distributionFactory);
                }
            };
        }

        @Provides
        protected ExecutorFactory createExecutorFactory() {
            return new DefaultExecutorFactory();
        }

        @Provides
        protected ExecutorServiceFactory createExecutorServiceFactory() {
            return new DefaultExecutorServiceFactory();
        }

        @Provides
        protected Clock createTimeProvider() {
            return Time.clock();
        }

        @Provides
        protected DistributionFactory createDistributionFactory(Clock clock) {
            return new DistributionFactory(clock);
        }

        @Provides
        protected ToolingImplementationLoader createToolingImplementationLoader() {
            return new SynchronizedToolingImplementationLoader(new CachingToolingImplementationLoader(new DefaultToolingImplementationLoader()));
        }

        @Provides
        protected BuildOperationIdFactory createBuildOperationIdFactory() {
            return new DefaultBuildOperationIdFactory();
        }

        @Provides
        protected LoggingProvider createLoggingProvider(Clock clock, BuildOperationIdFactory buildOperationIdFactory) {
            return new SynchronizedLogging(clock, buildOperationIdFactory);
        }

        @Provides
        protected ConnectionFactory createConnectionFactory(ToolingImplementationLoader toolingImplementationLoader, ExecutorFactory executorFactory, LoggingProvider loggingProvider) {
            return new ConnectionFactory(toolingImplementationLoader, executorFactory, loggingProvider);
        }
    }
}
