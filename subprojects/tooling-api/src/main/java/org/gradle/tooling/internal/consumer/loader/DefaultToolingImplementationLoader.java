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
package org.gradle.tooling.internal.consumer.loader;

import org.gradle.api.JavaVersion;
import org.gradle.initialization.BuildCancellationToken;
import org.gradle.internal.Factory;
import org.gradle.internal.classloader.FilteringClassLoader;
import org.gradle.internal.classloader.VisitableURLClassLoader;
import org.gradle.internal.classpath.ClassPath;
import org.gradle.internal.logging.progress.ProgressLoggerFactory;
import org.gradle.internal.service.DefaultServiceLocator;
import org.gradle.internal.service.ServiceLocator;
import org.gradle.tooling.GradleConnectionException;
import org.gradle.tooling.UnsupportedVersionException;
import org.gradle.tooling.internal.adapter.ProtocolToModelAdapter;
import org.gradle.tooling.internal.consumer.ConnectionParameters;
import org.gradle.tooling.internal.consumer.Distribution;
import org.gradle.tooling.internal.consumer.connection.AbstractConsumerConnection;
import org.gradle.tooling.internal.consumer.connection.ConsumerConnection;
import org.gradle.tooling.internal.consumer.connection.NoToolingApiConnection;
import org.gradle.tooling.internal.consumer.connection.NotifyDaemonsAboutChangedPathsConsumerConnection;
import org.gradle.tooling.internal.consumer.connection.ParameterAcceptingConsumerConnection;
import org.gradle.tooling.internal.consumer.connection.ParameterValidatingConsumerConnection;
import org.gradle.tooling.internal.consumer.connection.PhasedActionAwareConsumerConnection;
import org.gradle.tooling.internal.consumer.connection.StopWhenIdleConsumerConnection;
import org.gradle.tooling.internal.consumer.connection.TestExecutionConsumerConnection;
import org.gradle.tooling.internal.consumer.connection.UnsupportedOlderVersionConnection;
import org.gradle.tooling.internal.consumer.converters.ConsumerTargetTypeProvider;
import org.gradle.tooling.internal.consumer.versioning.ModelMapping;
import org.gradle.tooling.internal.consumer.versioning.VersionDetails;
import org.gradle.tooling.internal.protocol.ConnectionVersion4;
import org.gradle.tooling.internal.protocol.InternalBuildProgressListener;
import org.gradle.tooling.internal.protocol.InternalInvalidatableVirtualFileSystemConnection;
import org.gradle.tooling.internal.protocol.InternalParameterAcceptingConnection;
import org.gradle.tooling.internal.protocol.InternalPhasedActionConnection;
import org.gradle.tooling.internal.protocol.InternalStopWhenIdleConnection;
import org.gradle.tooling.internal.protocol.test.InternalTestExecutionConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

/**
 * Loads the tooling API implementation of the Gradle version that will run the build (the "provider").
 * Adapts the rather clunky cross-version interface to the more readable interface of the TAPI client.
 */
public class DefaultToolingImplementationLoader implements ToolingImplementationLoader {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultToolingImplementationLoader.class);
    private final ClassLoader classLoader;

    public DefaultToolingImplementationLoader() {
        this(DefaultToolingImplementationLoader.class.getClassLoader());
    }

    DefaultToolingImplementationLoader(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    @Override
    public ConsumerConnection create(Distribution distribution, ProgressLoggerFactory progressLoggerFactory, InternalBuildProgressListener progressListener, ConnectionParameters connectionParameters, BuildCancellationToken cancellationToken) {
        LOGGER.debug("Using tooling provider from {}", distribution.getDisplayName());
        ClassLoader serviceClassLoader = createImplementationClassLoader(distribution, progressLoggerFactory, progressListener, connectionParameters.getGradleUserHomeDir(), cancellationToken);
        ServiceLocator serviceLocator = new DefaultServiceLocator(serviceClassLoader);
        try {
            Factory<ConnectionVersion4> factory = serviceLocator.findFactory(ConnectionVersion4.class);
            if (factory == null) {
                return new NoToolingApiConnection(distribution);
            }
            ConnectionVersion4 connection = factory.create();

            ProtocolToModelAdapter adapter = new ProtocolToModelAdapter(new ConsumerTargetTypeProvider());
            ModelMapping modelMapping = new ModelMapping();
            if (connection instanceof InternalStopWhenIdleConnection) {
                return createConnection(new StopWhenIdleConsumerConnection(connection, modelMapping, adapter), connectionParameters);
            } else if (connection instanceof InternalInvalidatableVirtualFileSystemConnection) {
                return createConnection(new NotifyDaemonsAboutChangedPathsConsumerConnection(connection, modelMapping, adapter), connectionParameters);
            } else if (connection instanceof InternalPhasedActionConnection) {
                return createConnection(new PhasedActionAwareConsumerConnection(connection, modelMapping, adapter), connectionParameters);
            } else if (connection instanceof InternalParameterAcceptingConnection) {
                return createConnection(new ParameterAcceptingConsumerConnection(connection, modelMapping, adapter), connectionParameters);
            } else if (connection instanceof InternalTestExecutionConnection) {
                return createConnection(new TestExecutionConsumerConnection(connection, modelMapping, adapter), connectionParameters);
            } else {
                return new UnsupportedOlderVersionConnection(connection, adapter);
            }
        } catch (UnsupportedVersionException e) {
            throw e;
        } catch (Throwable t) {
            throw new GradleConnectionException(String.format("Could not create an instance of Tooling API implementation using the specified %s.", distribution.getDisplayName()), t);
        }
    }

    private ConsumerConnection createConnection(AbstractConsumerConnection adaptedConnection, ConnectionParameters connectionParameters) {
        adaptedConnection.configure(connectionParameters);
        VersionDetails versionDetails = adaptedConnection.getVersionDetails();
        return new ParameterValidatingConsumerConnection(versionDetails, adaptedConnection);
    }

    private ClassLoader createImplementationClassLoader(Distribution distribution, ProgressLoggerFactory progressLoggerFactory, InternalBuildProgressListener progressListener, File userHomeDir, BuildCancellationToken cancellationToken) {
        ClassPath implementationClasspath = distribution.getToolingImplementationClasspath(progressLoggerFactory, progressListener, userHomeDir, cancellationToken);
        LOGGER.debug("Using tooling provider classpath: {}", implementationClasspath);
        FilteringClassLoader.Spec filterSpec = new FilteringClassLoader.Spec();
        filterSpec.allowPackage("org.gradle.tooling.internal.protocol");
        filterSpec.allowClass(JavaVersion.class);
        FilteringClassLoader filteringClassLoader = new FilteringClassLoader(classLoader, filterSpec);
        return new VisitableURLClassLoader("tooling-implementation-loader", filteringClassLoader, implementationClasspath);
    }
}
