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
import org.gradle.initialization.BuildLayoutParameters;
import org.gradle.internal.concurrent.CompositeStoppable;
import org.gradle.internal.jvm.UnsupportedJavaRuntimeException;
import org.gradle.internal.logging.services.LoggingServiceRegistry;
import org.gradle.internal.nativeintegration.services.NativeServices;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.internal.service.ServiceRegistryBuilder;
import org.gradle.tooling.UnsupportedVersionException;
import org.gradle.tooling.internal.adapter.ProtocolToModelAdapter;
import org.gradle.tooling.internal.protocol.BuildActionRunner;
import org.gradle.tooling.internal.protocol.BuildExceptionVersion1;
import org.gradle.tooling.internal.protocol.BuildOperationParametersVersion1;
import org.gradle.tooling.internal.protocol.BuildParameters;
import org.gradle.tooling.internal.protocol.BuildParametersVersion1;
import org.gradle.tooling.internal.protocol.BuildResult;
import org.gradle.tooling.internal.protocol.ConfigurableConnection;
import org.gradle.tooling.internal.protocol.ConnectionMetaDataVersion1;
import org.gradle.tooling.internal.protocol.ConnectionParameters;
import org.gradle.tooling.internal.protocol.ConnectionVersion4;
import org.gradle.tooling.internal.protocol.InternalBuildAction;
import org.gradle.tooling.internal.protocol.InternalBuildActionExecutor;
import org.gradle.tooling.internal.protocol.InternalBuildActionVersion2;
import org.gradle.tooling.internal.protocol.InternalCancellableConnection;
import org.gradle.tooling.internal.protocol.InternalCancellationToken;
import org.gradle.tooling.internal.protocol.InternalConnection;
import org.gradle.tooling.internal.protocol.InternalParameterAcceptingConnection;
import org.gradle.tooling.internal.protocol.InternalPhasedAction;
import org.gradle.tooling.internal.protocol.InternalPhasedActionConnection;
import org.gradle.tooling.internal.protocol.InternalUnsupportedModelException;
import org.gradle.tooling.internal.protocol.ModelBuilder;
import org.gradle.tooling.internal.protocol.ModelIdentifier;
import org.gradle.tooling.internal.protocol.PhasedActionResultListener;
import org.gradle.tooling.internal.protocol.ProjectVersion3;
import org.gradle.tooling.internal.protocol.ShutdownParameters;
import org.gradle.tooling.internal.protocol.StoppableConnection;
import org.gradle.tooling.internal.protocol.exceptions.InternalUnsupportedBuildArgumentException;
import org.gradle.tooling.internal.protocol.test.InternalTestExecutionConnection;
import org.gradle.tooling.internal.protocol.test.InternalTestExecutionRequest;
import org.gradle.tooling.internal.provider.connection.BuildLogLevelMixIn;
import org.gradle.tooling.internal.provider.connection.ProviderBuildResult;
import org.gradle.tooling.internal.provider.connection.ProviderConnectionParameters;
import org.gradle.tooling.internal.provider.connection.ProviderOperationParameters;
import org.gradle.tooling.internal.provider.test.ProviderInternalTestExecutionRequest;
import org.gradle.util.DeprecationLogger;
import org.gradle.util.GradleVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.File;

public class DefaultConnection implements ConnectionVersion4, InternalConnection, BuildActionRunner,
    ConfigurableConnection, ModelBuilder, InternalBuildActionExecutor, InternalCancellableConnection, InternalParameterAcceptingConnection,
    StoppableConnection, InternalTestExecutionConnection, InternalPhasedActionConnection {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultConnection.class);
    private static final String UNSUPPORTED_MESSAGE = "Support for clients using a tooling API version older than 3.0 was removed in Gradle 5.0. %sYou should upgrade your tooling API client to version 3.0 or later.";

    private static final GradleVersion MIN_CLIENT_VERSION = GradleVersion.version("3.0");
    private ProtocolToModelAdapter adapter;
    private ServiceRegistry services;
    private ProviderConnection connection;
    @Nullable // not provided by older client versions
    private GradleVersion consumerVersion;

    /**
     * This is used by consumers 1.0-milestone-3 and later
     */
    public DefaultConnection() {
        LOGGER.debug("Tooling API provider {} created.", GradleVersion.current().getVersion());
    }

    /**
     * This is used by consumers 1.2-rc-1 and later.
     */
    @Override
    public void configure(ConnectionParameters parameters) {
        assertUsingSupportedJavaVersion();
        ProviderConnectionParameters providerConnectionParameters = new ProtocolToModelAdapter().adapt(ProviderConnectionParameters.class, parameters);
        File gradleUserHomeDir = providerConnectionParameters.getGradleUserHomeDir(null);
        if (gradleUserHomeDir == null) {
            gradleUserHomeDir = new BuildLayoutParameters().getGradleUserHomeDir();
        }
        initializeServices(gradleUserHomeDir);
        connection.configure(providerConnectionParameters);
        consumerVersion = GradleVersion.version(providerConnectionParameters.getConsumerVersion());
    }

    private void assertUsingSupportedJavaVersion() {
        try {
            UnsupportedJavaRuntimeException.assertUsingVersion("Gradle", JavaVersion.VERSION_1_8);
        } catch (IllegalArgumentException e) {
            LOGGER.warn(e.getMessage());
        }
    }

    private void initializeServices(File gradleUserHomeDir) {
        NativeServices.initialize(gradleUserHomeDir);
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
     * This is used by consumers 1.0-milestone-3 and later
     */
    @Override
    public ConnectionMetaDataVersion1 getMetaData() {
        return new DefaultConnectionMetaData();
    }

    /**
     * This is used by consumers 1.0-milestone-3 and later
     */
    @Override
    @Deprecated
    public void stop() {
        // We don't do anything here, as older consumers call this method when the project connection is closed but then later attempt to reuse the connection
    }

    /**
     * This is used by consumers 2.2-rc-1 and later
     */
    @Override
    public void shutdown(ShutdownParameters parameters) {
        CompositeStoppable.stoppable(services).stop();
    }

    /**
     * This is used by consumers 1.0-milestone-3 to 1.1.
     */
    @Override
    @Deprecated
    public void executeBuild(BuildParametersVersion1 buildParameters, BuildOperationParametersVersion1 operationParameters) {
        throw unsupportedConnectionException();
    }

    /**
     * This is used by consumers 1.0-milestone-3 to 1.0-milestone-7
     */
    @Override
    @Deprecated
    public ProjectVersion3 getModel(Class<? extends ProjectVersion3> type, BuildOperationParametersVersion1 parameters) {
        throw unsupportedConnectionException();
    }

    /**
     * This is used by consumers 1.0-milestone-8 to 1.1
     */
    @Override
    @Deprecated
    public <T> T getTheModel(Class<T> type, BuildOperationParametersVersion1 parameters) {
        throw unsupportedConnectionException();
    }

    /**
     * This is used by consumers 1.2-rc-1 to 1.5
     */
    @Override
    @Deprecated
    public <T> BuildResult<T> run(Class<T> type, BuildParameters buildParameters) throws UnsupportedOperationException, IllegalStateException {
        throw unsupportedConnectionException();
    }

    /**
     * This is used by consumers 1.6-rc-1 to 2.0
     */
    @Override
    public BuildResult<?> getModel(ModelIdentifier modelIdentifier, BuildParameters operationParameters) throws UnsupportedOperationException, IllegalStateException {
        throw unsupportedConnectionException();
    }

    /**
     * This is used by consumers 2.1-rc-1 and later
     */
    @Override
    public BuildResult<?> getModel(ModelIdentifier modelIdentifier, InternalCancellationToken cancellationToken, BuildParameters operationParameters) throws BuildExceptionVersion1, InternalUnsupportedModelException, InternalUnsupportedBuildArgumentException, IllegalStateException {
        ProviderOperationParameters providerParameters = validateAndConvert(operationParameters);
        BuildCancellationToken buildCancellationToken = new InternalCancellationTokenAdapter(cancellationToken);
        Object result = connection.run(modelIdentifier.getName(), buildCancellationToken, providerParameters);
        return new ProviderBuildResult<Object>(result);
    }

    /**
     * This is used by consumers 1.8-rc-1 to 2.0
     */
    @Override
    public <T> BuildResult<T> run(InternalBuildAction<T> action, BuildParameters operationParameters) throws BuildExceptionVersion1, InternalUnsupportedBuildArgumentException, IllegalStateException {
        throw unsupportedConnectionException();
    }

    /**
     * This is used by consumers 2.1-rc-1 to 4.3
     */
    @Override
    public <T> BuildResult<T> run(InternalBuildAction<T> action, InternalCancellationToken cancellationToken, BuildParameters operationParameters)
        throws BuildExceptionVersion1, InternalUnsupportedBuildArgumentException, IllegalStateException {
        ProviderOperationParameters providerParameters = validateAndConvert(operationParameters);
        BuildCancellationToken buildCancellationToken = new InternalCancellationTokenAdapter(cancellationToken);
        Object results = connection.run(action, buildCancellationToken, providerParameters);
        return new ProviderBuildResult<T>((T) results);
    }

    /**
     * This is used by consumers 4.4 and later
     */
    @Override
    public <T> BuildResult<T> run(InternalBuildActionVersion2<T> action, InternalCancellationToken cancellationToken, BuildParameters operationParameters)
        throws BuildExceptionVersion1, InternalUnsupportedBuildArgumentException, IllegalStateException {
        ProviderOperationParameters providerParameters = validateAndConvert(operationParameters);
        BuildCancellationToken buildCancellationToken = new InternalCancellationTokenAdapter(cancellationToken);
        Object results = connection.run(action, buildCancellationToken, providerParameters);
        return new ProviderBuildResult<T>((T) results);
    }

    /**
     * This is used by consumers 4.8 and later
     */
    @Override
    public BuildResult<?> run(InternalPhasedAction phasedAction, PhasedActionResultListener listener, InternalCancellationToken cancellationToken, BuildParameters operationParameters) {
        ProviderOperationParameters providerParameters = validateAndConvert(operationParameters);
        BuildCancellationToken buildCancellationToken = new InternalCancellationTokenAdapter(cancellationToken);
        Object results = connection.runPhasedAction(phasedAction, listener, buildCancellationToken, providerParameters);
        return new ProviderBuildResult<Object>(results);
    }

    /**
     * This is used by consumers 2.6-rc-1 and later
     */
    @Override
    public BuildResult<?> runTests(InternalTestExecutionRequest testExecutionRequest, InternalCancellationToken cancellationToken, BuildParameters operationParameters)
        throws BuildExceptionVersion1, InternalUnsupportedBuildArgumentException, IllegalStateException {
        ProviderOperationParameters providerParameters = validateAndConvert(operationParameters);
        ProviderInternalTestExecutionRequest testExecutionRequestVersion2 = adapter.adapt(ProviderInternalTestExecutionRequest.class, testExecutionRequest);
        BuildCancellationToken buildCancellationToken = new InternalCancellationTokenAdapter(cancellationToken);
        Object results = connection.runTests(testExecutionRequestVersion2, buildCancellationToken, providerParameters);
        return new ProviderBuildResult<Object>(results);
    }

    private ProviderOperationParameters validateAndConvert(BuildParameters buildParameters) {
        LOGGER.info("Tooling API is using target Gradle version: {}.", GradleVersion.current().getVersion());
        assertUsingSupportedJavaVersion();

        checkUnsupportedTapiVersion();
        ProviderOperationParameters parameters = adapter.builder(ProviderOperationParameters.class).mixInTo(ProviderOperationParameters.class, BuildLogLevelMixIn.class).build(buildParameters);

        DeprecationLogger.reset();
        return parameters;
    }

    private UnsupportedVersionException unsupportedConnectionException() {
        return new UnsupportedVersionException(String.format(UNSUPPORTED_MESSAGE, createCurrentVersionMessage()));
    }

    private String createCurrentVersionMessage() {
        if (consumerVersion == null) {
            return "";
        } else {
            // Consumer version is provided by client 1.2 and later
            return String.format("You are currently using tooling API version %s. ", consumerVersion.getVersion());
        }
    }

    private void checkUnsupportedTapiVersion() {
        if (consumerVersion == null || consumerVersion.compareTo(MIN_CLIENT_VERSION) < 0) {
            throw unsupportedConnectionException();
        }
    }
}
