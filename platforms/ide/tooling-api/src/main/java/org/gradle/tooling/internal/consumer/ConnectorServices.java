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

import com.google.common.annotations.VisibleForTesting;
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
import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.internal.consumer.loader.CachingToolingImplementationLoader;
import org.gradle.tooling.internal.consumer.loader.DefaultToolingImplementationLoader;
import org.gradle.tooling.internal.consumer.loader.SynchronizedToolingImplementationLoader;
import org.gradle.tooling.internal.consumer.loader.ToolingImplementationLoader;
import org.jspecify.annotations.NullMarked;

/**
 * Internal API that is used for cross-version TAPI client testing.
 */
@NullMarked
public class ConnectorServices {

    private static GradleConnectorFactory sharedConnectorFactory = createConnectorFactory();

    public static CancellationTokenSource createCancellationTokenSource() {
        return new DefaultCancellationTokenSource();
    }

    public static GradleConnector createConnector() {
        return sharedConnectorFactory.createConnector();
    }

    public static void close() {
        sharedConnectorFactory.close();
    }

    /**
     * Resets the state of connector services.
     * <p>
     * Used for cross-version testing of the lifecycle of the connector services.
     */
    @VisibleForTesting
    public static void reset() {
        close();
        sharedConnectorFactory = createConnectorFactory();
    }

    /**
     * Used for cross-version testing of the lifecycle of the connector services.
     */
    @VisibleForTesting
    public static GradleConnectorFactory createConnectorFactory() {
        return new DefaultGradleConnectorFactory();
    }

    @NullMarked
    private static class DefaultGradleConnectorFactory implements GradleConnectorFactory {
        private final CloseableServiceRegistry ownerRegistry = ConnectorServiceRegistry.create();

        @Override
        public GradleConnector createConnector() {
            return ownerRegistry.get(GradleConnectorFactory.class).createConnector();
        }

        @Override
        public void close() {
            ownerRegistry.close();
        }
    }

    /**
     * Exists for the purpose of creating {@link GradleConnectorFactory}.
     * <p>
     * The service registry is used to simplify setting up and tearing down the dependencies.
     */
    @NullMarked
    private static class ConnectorServiceRegistry implements ServiceRegistrationProvider {

        private static CloseableServiceRegistry create() {
            return ServiceRegistryBuilder.builder()
                .displayName("connector services")
                .provider(new ConnectorServiceRegistry())
                .build();
        }

        @Provides
        protected GradleConnectorFactory createConnectorFactory(ConnectionFactory connectionFactory, DistributionFactory distributionFactory) {
            return new GradleConnectorFactory() {
                @Override
                public GradleConnector createConnector() {
                    return new DefaultGradleConnector(connectionFactory, distributionFactory);
                }

                @Override
                public void close() {}
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
