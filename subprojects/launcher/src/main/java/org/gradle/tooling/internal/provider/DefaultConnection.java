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
package org.gradle.tooling.internal.provider;

import org.gradle.api.JavaVersion;
import org.gradle.initialization.BuildCancellationToken;
import org.gradle.initialization.FixedBuildCancellationToken;
import org.gradle.internal.concurrent.CompositeStoppable;
import org.gradle.internal.jvm.UnsupportedJavaRuntimeException;
import org.gradle.internal.nativeintegration.services.NativeServices;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.internal.service.ServiceRegistryBuilder;
import org.gradle.logging.LoggingServiceRegistry;
import org.gradle.tooling.UnsupportedVersionException;
import org.gradle.tooling.internal.adapter.ProtocolToModelAdapter;
import org.gradle.tooling.internal.consumer.versioning.ModelMapping;
import org.gradle.tooling.internal.protocol.*;
import org.gradle.tooling.internal.protocol.exceptions.InternalUnsupportedBuildArgumentException;
import org.gradle.tooling.internal.provider.connection.BuildLogLevelMixIn;
import org.gradle.tooling.internal.provider.connection.ProviderBuildResult;
import org.gradle.tooling.internal.provider.connection.ProviderConnectionParameters;
import org.gradle.tooling.internal.provider.connection.ProviderOperationParameters;
import org.gradle.util.GradleVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultConnection implements InternalConnection, BuildActionRunner,
        ConfigurableConnection, ModelBuilder, InternalBuildActionExecutor, InternalCancellableConnection, StoppableConnection {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultConnection.class);
    private final ProtocolToModelAdapter adapter;
    private final ServiceRegistry services;
    private final ProviderConnection connection;

    /**
     * This is used by consumers 1.0-milestone-3 and later
     */
    public DefaultConnection() {
        LOGGER.debug("Tooling API provider {} created.", GradleVersion.current().getVersion());
        LoggingServiceRegistry loggingServices = LoggingServiceRegistry.newEmbeddableLogging();
        services = ServiceRegistryBuilder.builder()
                .displayName("Connection services")
                .parent(loggingServices)
                .parent(NativeServices.getInstance())
                .provider(new ConnectionScopeServices(loggingServices)).build();
        adapter = services.get(ProtocolToModelAdapter.class);
        connection = services.get(ProviderConnection.class);
    }

    /**
     * This is used by consumers 1.2-rc-1 and later.
     */
    public void configure(ConnectionParameters parameters) {
        ProviderConnectionParameters providerConnectionParameters = adapter.adapt(ProviderConnectionParameters.class, parameters);
        connection.configure(providerConnectionParameters);
    }

    /**
     * This method was used by consumers 1.0-rc-1 through to 1.1. Later consumers use {@link #configure(ConnectionParameters)} instead.
     */
    public void configureLogging(final boolean verboseLogging) {
        // Ignore - we don't support these consumer versions any more
    }

    /**
     * This is used by consumers 1.0-milestone-3 and later
     */
    public ConnectionMetaDataVersion1 getMetaData() {
        return new DefaultConnectionMetaData();
    }

    /**
     * This is used by consumers 1.0-milestone-3 and later
     */
    @Deprecated
    public void stop() {
        // We don't do anything here, as older consumers call this method when the project connection is closed but then later attempt to reuse the connection
    }

    /**
     * This is used by consumers 2.2-rc-1 and later
     */
    public void shutdown(ShutdownParameters parameters) {
        CompositeStoppable.stoppable(services).stop();
    }

    /**
     * This is used by consumers 1.0-milestone-3 to 1.1.
     */
    @Deprecated
    public void executeBuild(BuildParametersVersion1 buildParameters, BuildOperationParametersVersion1 operationParameters) {
        throw unsupportedConnectionException();
    }

    /**
     * This is used by consumers 1.0-milestone-3 to 1.0-milestone-7
     */
    @Deprecated
    public ProjectVersion3 getModel(Class<? extends ProjectVersion3> type, BuildOperationParametersVersion1 parameters) {
        throw unsupportedConnectionException();
    }

    /**
     * This is used by consumers 1.0-milestone-8 to 1.1
     */
    @Deprecated
    public <T> T getTheModel(Class<T> type, BuildOperationParametersVersion1 parameters) {
        throw unsupportedConnectionException();
    }

    /**
     * This is used by consumers 1.2-rc-1 to 1.5
     */
    @Deprecated
    public <T> BuildResult<T> run(Class<T> type, BuildParameters buildParameters) throws UnsupportedOperationException, IllegalStateException {
        validateCanRun();
        ProviderOperationParameters providerParameters = toProviderParameters(buildParameters);
        String modelName = new ModelMapping().getModelNameFromProtocolType(type);
        T result = (T) connection.run(modelName, new FixedBuildCancellationToken(), providerParameters);
        return new ProviderBuildResult<T>(result);
    }

    /**
     * This is used by consumers 1.6-rc-1 and later
     */
    public BuildResult<?> getModel(ModelIdentifier modelIdentifier, BuildParameters operationParameters) throws UnsupportedOperationException, IllegalStateException {
        validateCanRun();
        ProviderOperationParameters providerParameters = toProviderParameters(operationParameters);
        Object result = connection.run(modelIdentifier.getName(), new FixedBuildCancellationToken(), providerParameters);
        return new ProviderBuildResult<Object>(result);
    }

    /**
     * This is used by consumers 2.1-rc-1 and later
     */
    public BuildResult<?> getModel(ModelIdentifier modelIdentifier, InternalCancellationToken cancellationToken, BuildParameters operationParameters) throws BuildExceptionVersion1, InternalUnsupportedModelException, InternalUnsupportedBuildArgumentException, IllegalStateException {
        validateCanRun();
        ProviderOperationParameters providerParameters = toProviderParameters(operationParameters);
        BuildCancellationToken buildCancellationToken = new InternalCancellationTokenAdapter(cancellationToken);
        Object result = connection.run(modelIdentifier.getName(), buildCancellationToken, providerParameters);
        return new ProviderBuildResult<Object>(result);
    }

    /**
     * This is used by consumers 1.8-rc-1 and later.
     */
    public <T> BuildResult<T> run(InternalBuildAction<T> action, BuildParameters operationParameters) throws BuildExceptionVersion1, InternalUnsupportedBuildArgumentException, IllegalStateException {
        validateCanRun();
        ProviderOperationParameters providerParameters = toProviderParameters(operationParameters);
        Object results = connection.run(action, new FixedBuildCancellationToken(), providerParameters);
        return new ProviderBuildResult<T>((T) results);
    }

    /**
     * This is used by consumers 2.1-rc-1 and later.
     */
    public <T> BuildResult<T> run(InternalBuildAction<T> action, InternalCancellationToken cancellationToken, BuildParameters operationParameters)
            throws BuildExceptionVersion1, InternalUnsupportedBuildArgumentException, IllegalStateException {
        validateCanRun();
        ProviderOperationParameters providerParameters = toProviderParameters(operationParameters);
        BuildCancellationToken buildCancellationToken = new InternalCancellationTokenAdapter(cancellationToken);
        Object results = connection.run(action, buildCancellationToken, providerParameters);
        return new ProviderBuildResult<T>((T) results);
    }

    private void validateCanRun() {
        LOGGER.info("Tooling API is using target Gradle version: {}.", GradleVersion.current().getVersion());
        if (!JavaVersion.current().isJava6Compatible()) {
            throw UnsupportedJavaRuntimeException.usingUnsupportedVersion("Gradle", JavaVersion.VERSION_1_6);
        }
    }

    private UnsupportedVersionException unsupportedConnectionException() {
        return new UnsupportedVersionException("Support for clients using a tooling API version older than 1.2 was removed in Gradle 2.0. You should upgrade your tooling API client to version 1.2 or later.");
    }

    private ProviderOperationParameters toProviderParameters(BuildParameters buildParameters) {
        return adapter.adapt(ProviderOperationParameters.class, buildParameters, BuildLogLevelMixIn.class);
    }
}