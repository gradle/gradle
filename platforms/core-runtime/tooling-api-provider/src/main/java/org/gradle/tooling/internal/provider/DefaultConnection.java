/*
 * Copyright 2024 the original author or authors.
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

import org.gradle.initialization.BuildCancellationToken;
import org.gradle.initialization.BuildLayoutParameters;
import org.gradle.internal.Cast;
import org.gradle.internal.buildprocess.BuildProcessState;
import org.gradle.internal.deprecation.DeprecationLogger;
import org.gradle.internal.installation.CurrentGradleInstallation;
import org.gradle.internal.instrumentation.agent.AgentStatus;
import org.gradle.internal.logging.services.LoggingServiceRegistry;
import org.gradle.internal.nativeintegration.services.NativeServices;
import org.gradle.internal.nativeintegration.services.NativeServices.NativeServicesMode;
import org.gradle.internal.service.CloseableServiceRegistry;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.internal.service.ServiceRegistryBuilder;
import org.gradle.internal.service.scopes.GlobalScopeServices;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.tooling.UnsupportedVersionException;
import org.gradle.tooling.internal.adapter.ProtocolToModelAdapter;
import org.gradle.tooling.internal.consumer.DefaultGradleConnector;
import org.gradle.tooling.internal.protocol.BuildParameters;
import org.gradle.tooling.internal.protocol.BuildResult;
import org.gradle.tooling.internal.protocol.ConfigurableConnection;
import org.gradle.tooling.internal.protocol.ConnectionMetaDataVersion1;
import org.gradle.tooling.internal.protocol.ConnectionParameters;
import org.gradle.tooling.internal.protocol.ConnectionVersion4;
import org.gradle.tooling.internal.protocol.InternalBuildActionVersion2;
import org.gradle.tooling.internal.protocol.InternalCancellableConnection;
import org.gradle.tooling.internal.protocol.InternalCancellationToken;
import org.gradle.tooling.internal.protocol.InternalInvalidatableVirtualFileSystemConnection;
import org.gradle.tooling.internal.protocol.InternalParameterAcceptingConnection;
import org.gradle.tooling.internal.protocol.InternalPhasedAction;
import org.gradle.tooling.internal.protocol.InternalPhasedActionConnection;
import org.gradle.tooling.internal.protocol.InternalStopWhenIdleConnection;
import org.gradle.tooling.internal.protocol.InternalUnsupportedModelException;
import org.gradle.tooling.internal.protocol.ModelIdentifier;
import org.gradle.tooling.internal.protocol.PhasedActionResultListener;
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
import org.gradle.util.GradleVersion;
import org.gradle.util.internal.IncubationLogger;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Implements the provider side of the tooling API.
 *
 * @see org.gradle.tooling.internal.consumer.loader.DefaultToolingImplementationLoader
 */
@SuppressWarnings("deprecation")
public class DefaultConnection implements ConnectionVersion4,
    ConfigurableConnection, InternalCancellableConnection, InternalParameterAcceptingConnection,
    StoppableConnection, InternalTestExecutionConnection, InternalPhasedActionConnection, InternalInvalidatableVirtualFileSystemConnection, InternalStopWhenIdleConnection {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultConnection.class);

    private static final String MIN_CLIENT_VERSION_STR = DefaultGradleConnector.MINIMUM_SUPPORTED_GRADLE_VERSION.getVersion();
    public static final int GUARANTEED_TAPI_BACKWARDS_COMPATIBILITY = 5;

    private final ProtocolToModelAdapter adapter = new ProtocolToModelAdapter();

    private @Nullable ServiceRegistry loggingServices;
    private @Nullable CloseableServiceRegistry clientServices;
    private @Nullable BuildProcessState embeddedDaemonState;

    @Nullable // not provided by older client versions
    private GradleVersion consumerVersion;
    private boolean verboseLogging;

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
        ProviderConnectionParameters providerConnectionParameters = new ProtocolToModelAdapter().adapt(ProviderConnectionParameters.class, parameters);
        File gradleUserHomeDir = providerConnectionParameters.getGradleUserHomeDir(null);
        if (gradleUserHomeDir == null) {
            gradleUserHomeDir = new BuildLayoutParameters().getGradleUserHomeDir();
        }
        initializeServices(gradleUserHomeDir);
        consumerVersion = GradleVersion.version(providerConnectionParameters.getConsumerVersion());
        verboseLogging = providerConnectionParameters.getVerboseLogging();
    }

    private void initializeServices(File gradleUserHomeDir) {
        NativeServices.initializeOnClient(gradleUserHomeDir, NativeServicesMode.fromSystemProperties());
        this.loggingServices = LoggingServiceRegistry.newEmbeddableLogging();
    }

    /**
     * This is used by consumers 1.0-milestone-3 and later
     */
    @Override
    public ConnectionMetaDataVersion1 getMetaData() {
        return new DefaultConnectionMetaData();
    }

    /**
     * This is used by consumers 2.2-rc-1 and later
     */
    @Override
    public void shutdown(ShutdownParameters parameters) {
        if (embeddedDaemonState != null) {
            embeddedDaemonState.getServices().get(ShutdownCoordinator.class).stopAllDaemons();
            embeddedDaemonState.close();
            embeddedDaemonState = null;
        }
        if (clientServices != null) {
            clientServices.get(ShutdownCoordinator.class).stopAllDaemons();
            clientServices.close();
            clientServices = null;
        }
        loggingServices = null;
    }

    /**
     * This is used by consumers 2.1-rc-1 and later
     */
    @Override
    public BuildResult<?> getModel(ModelIdentifier modelIdentifier, InternalCancellationToken cancellationToken, BuildParameters operationParameters) throws org.gradle.tooling.internal.protocol.BuildExceptionVersion1, InternalUnsupportedModelException, InternalUnsupportedBuildArgumentException, IllegalStateException {
        ProviderOperationParameters providerParameters = validateAndConvert(operationParameters);
        BuildCancellationToken buildCancellationToken = new InternalCancellationTokenAdapter(cancellationToken);
        Object result = getConnection(providerParameters).run(modelIdentifier.getName(), buildCancellationToken, providerParameters);
        return new ProviderBuildResult<>(result);
    }

    /**
     * This is used by consumers 2.1-rc-1 to 4.3
     */
    @Override
    public <T> BuildResult<T> run(org.gradle.tooling.internal.protocol.InternalBuildAction<T> action, InternalCancellationToken cancellationToken, BuildParameters operationParameters)
        throws org.gradle.tooling.internal.protocol.BuildExceptionVersion1, InternalUnsupportedBuildArgumentException, IllegalStateException {
        ProviderOperationParameters providerParameters = validateAndConvert(operationParameters);
        BuildCancellationToken buildCancellationToken = new InternalCancellationTokenAdapter(cancellationToken);
        Object results = getConnection(providerParameters).run(action, buildCancellationToken, providerParameters);
        return new ProviderBuildResult<>(Cast.uncheckedNonnullCast(results));
    }

    /**
     * This is used by consumers 4.4 and later
     */
    @Override
    public <T> BuildResult<T> run(InternalBuildActionVersion2<T> action, InternalCancellationToken cancellationToken, BuildParameters operationParameters)
        throws org.gradle.tooling.internal.protocol.BuildExceptionVersion1, InternalUnsupportedBuildArgumentException, IllegalStateException {
        ProviderOperationParameters providerParameters = validateAndConvert(operationParameters);
        BuildCancellationToken buildCancellationToken = new InternalCancellationTokenAdapter(cancellationToken);
        Object results = getConnection(providerParameters).run(action, buildCancellationToken, providerParameters);
        return new ProviderBuildResult<>(Cast.uncheckedNonnullCast(results));
    }

    /**
     * This is used by consumers 4.8 and later
     */
    @Override
    public BuildResult<?> run(InternalPhasedAction phasedAction, PhasedActionResultListener listener, InternalCancellationToken cancellationToken, BuildParameters operationParameters) {
        ProviderOperationParameters providerParameters = validateAndConvert(operationParameters);
        BuildCancellationToken buildCancellationToken = new InternalCancellationTokenAdapter(cancellationToken);
        Object results = getConnection(providerParameters).runPhasedAction(phasedAction, listener, buildCancellationToken, providerParameters);
        return new ProviderBuildResult<>(results);
    }

    /**
     * This is used by consumers 2.6-rc-1 and later
     */
    @Override
    public BuildResult<?> runTests(InternalTestExecutionRequest testExecutionRequest, InternalCancellationToken cancellationToken, BuildParameters operationParameters)
        throws org.gradle.tooling.internal.protocol.BuildExceptionVersion1, InternalUnsupportedBuildArgumentException, IllegalStateException {
        ProviderOperationParameters providerParameters = validateAndConvert(operationParameters);
        ProviderInternalTestExecutionRequest testExecutionRequestVersion2 = adapter.adapt(ProviderInternalTestExecutionRequest.class, testExecutionRequest);
        BuildCancellationToken buildCancellationToken = new InternalCancellationTokenAdapter(cancellationToken);
        Object results = getConnection(providerParameters).runTests(testExecutionRequestVersion2, buildCancellationToken, providerParameters);
        return new ProviderBuildResult<>(results);
    }

    private ProviderOperationParameters validateAndConvert(BuildParameters buildParameters) {
        LOGGER.info("Tooling API is using target Gradle version: {}.", GradleVersion.current().getVersion());

        checkUnsupportedTapiVersion();
        ProviderOperationParameters parameters = adapter.builder(ProviderOperationParameters.class).mixInTo(ProviderOperationParameters.class, BuildLogLevelMixIn.class).build(buildParameters);

        DeprecationLogger.reset();
        IncubationLogger.reset();
        return parameters;
    }

    private UnsupportedVersionException unsupportedConnectionException() {
        return new UnsupportedVersionException(String.format("Support for clients using a tooling API version older than %s was removed in Gradle %d.0. %sYou should upgrade your tooling API client to version %s or later.",
            MIN_CLIENT_VERSION_STR,
            DefaultGradleConnector.MINIMAL_CLIENT_MAJOR_VERSION + GUARANTEED_TAPI_BACKWARDS_COMPATIBILITY,
            createCurrentVersionMessage(),
            MIN_CLIENT_VERSION_STR));
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
        if (consumerVersion == null || consumerVersion.compareTo(DefaultGradleConnector.MINIMUM_SUPPORTED_GRADLE_VERSION) < 0) {
            throw unsupportedConnectionException();
        }
    }

    @Override
    public void notifyDaemonsAboutChangedPaths(List<String> changedPaths, BuildParameters operationParameters) {
        ProviderOperationParameters providerParameters = validateAndConvert(operationParameters);
        getConnection(providerParameters).notifyDaemonsAboutChangedPaths(changedPaths, providerParameters);
    }

    @Override
    public void stopWhenIdle(BuildParameters operationParameters) {
        ProviderOperationParameters providerParameters = validateAndConvert(operationParameters);
        getConnection(providerParameters).stopWhenIdle(providerParameters);
    }

    private ProviderConnection getConnection(ProviderOperationParameters providerParameters) {
        return getServices(providerParameters).get(ProviderConnection.class);
    }

    private ServiceRegistry getServices(ProviderOperationParameters providerParameters) {
        if (Boolean.TRUE.equals(providerParameters.isEmbedded())) {
            if (embeddedDaemonState == null) {
                embeddedDaemonState = new BuildProcessState(
                    true,
                    AgentStatus.disabled(),
                    CurrentGradleInstallation.locate(),
                    Collections.singleton(new ConnectionScopeServices()),
                    Arrays.asList(
                        loggingServices,
                        NativeServices.getInstance()
                    )
                );
                configureServices(embeddedDaemonState.getServices());
            }
            return embeddedDaemonState.getServices();
        } else {
            if (clientServices == null) {
                clientServices = ServiceRegistryBuilder.builder()
                    .displayName("TAPI connection global services")
                    .scopeStrictly(Scope.Global.class)
                    .parent(loggingServices)
                    .parent(NativeServices.getInstance())
                    .provider(new GlobalScopeServices(
                        true,
                        AgentStatus.disabled(),
                        CurrentGradleInstallation.locate()
                    ))
                    .provider(new ConnectionScopeServices())
                    .build();
                configureServices(clientServices);
            }
            return clientServices;
        }
    }

    private void configureServices(ServiceRegistry services) {
        assert consumerVersion != null;
        services.get(ProviderConnection.class).configure(verboseLogging, consumerVersion);
    }
}
