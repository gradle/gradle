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

import org.gradle.api.JavaVersion;
import org.gradle.internal.Factory;
import org.gradle.internal.concurrent.DefaultExecutorFactory;
import org.gradle.internal.concurrent.ExecutorFactory;
import org.gradle.internal.jvm.UnsupportedJavaRuntimeException;
import org.gradle.internal.service.DefaultServiceRegistry;
import org.gradle.tooling.CancellationTokenSource;
import org.gradle.tooling.connection.GradleConnectionBuilder;
import org.gradle.tooling.internal.connection.DefaultGradleConnectionBuilder;
import org.gradle.tooling.internal.connection.GradleConnectionFactory;
import org.gradle.tooling.internal.consumer.loader.CachingToolingImplementationLoader;
import org.gradle.tooling.internal.consumer.loader.DefaultToolingImplementationLoader;
import org.gradle.tooling.internal.consumer.loader.SynchronizedToolingImplementationLoader;
import org.gradle.tooling.internal.consumer.loader.ToolingImplementationLoader;
import org.gradle.util.DeprecationLogger;

public class ConnectorServices {
    private static DefaultServiceRegistry singletonRegistry = new ConnectorServiceRegistry();

    public static DefaultGradleConnector createConnector() {
        checkJavaVersion();
        return singletonRegistry.getFactory(DefaultGradleConnector.class).create();
    }

    public static GradleConnectionBuilder createGradleConnectionBuilder() {
        checkJavaVersion();
        return singletonRegistry.getFactory(GradleConnectionBuilder.class).create();
    }

    public static CancellationTokenSource createCancellationTokenSource() {
        checkJavaVersion();
        return new DefaultCancellationTokenSource();
    }

    public static void close() {
        checkJavaVersion();
        singletonRegistry.close();
    }

    /**
     * Resets the state of connector services. Meant to be used only for testing!
     */
    public static void reset() {
        singletonRegistry.close();
        singletonRegistry = new ConnectorServiceRegistry();
    }

    private static void checkJavaVersion() {
        JavaVersion javaVersion = JavaVersion.current();
        if (!javaVersion.isJava6Compatible()) {
            throw UnsupportedJavaRuntimeException.usingUnsupportedVersion("Gradle Tooling API", JavaVersion.VERSION_1_6);
        }
        if (javaVersion == JavaVersion.VERSION_1_6) {
            DeprecationLogger.nagUserWith("Support for using the Gradle Tooling API with Java 6 is deprecated and will be removed in Gradle 3.0");
        }
    }

    private static class ConnectorServiceRegistry extends DefaultServiceRegistry {
        protected Factory<DefaultGradleConnector> createConnectorFactory(final ConnectionFactory connectionFactory, final DistributionFactory distributionFactory) {
            return new Factory<DefaultGradleConnector>() {
                public DefaultGradleConnector create() {
                    return new DefaultGradleConnector(connectionFactory, distributionFactory);
                }
            };
        }

        protected Factory<GradleConnectionBuilder> createGradleConnectionBuilder(final GradleConnectionFactory gradleConnectionFactory, final DistributionFactory distributionFactory) {
            return new Factory<GradleConnectionBuilder>() {
                public GradleConnectionBuilder create() {
                    return new DefaultGradleConnectionBuilder(gradleConnectionFactory, distributionFactory);
                }
            };
        }

        protected ExecutorFactory createExecutorFactory() {
            return new DefaultExecutorFactory();
        }

        protected ExecutorServiceFactory createExecutorServiceFactory() {
            return new DefaultExecutorServiceFactory();
        }

        protected DistributionFactory createDistributionFactory(ExecutorServiceFactory executorFactory) {
            return new DistributionFactory(executorFactory);
        }

        protected ToolingImplementationLoader createToolingImplementationLoader() {
            return new SynchronizedToolingImplementationLoader(new CachingToolingImplementationLoader(new DefaultToolingImplementationLoader()));
        }

        protected LoggingProvider createLoggingProvider() {
            return new SynchronizedLogging();
        }

        protected ConnectionFactory createConnectionFactory(ToolingImplementationLoader toolingImplementationLoader, ExecutorFactory executorFactory, LoggingProvider loggingProvider) {
            return new ConnectionFactory(toolingImplementationLoader, executorFactory, loggingProvider);
        }

        protected GradleConnectionFactory createGradleConnectionFactory(ToolingImplementationLoader toolingImplementationLoader, ExecutorFactory executorFactory, LoggingProvider loggingProvider) {
            return new GradleConnectionFactory(toolingImplementationLoader, executorFactory, loggingProvider);
        }
    }
}
