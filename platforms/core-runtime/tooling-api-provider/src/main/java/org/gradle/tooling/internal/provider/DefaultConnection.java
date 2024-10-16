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
import org.gradle.internal.classpath.ClassPath;
import org.gradle.internal.deprecation.DeprecationLogger;
import org.gradle.internal.instrumentation.agent.AgentStatus;
import org.gradle.internal.jvm.UnsupportedJavaRuntimeException;
import org.gradle.internal.logging.services.LoggingServiceRegistry;
import org.gradle.internal.nativeintegration.services.NativeServices;
import org.gradle.internal.nativeintegration.services.NativeServices.NativeServicesMode;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.internal.service.ServiceRegistryBuilder;
import org.gradle.tooling.UnsupportedVersionException;
import org.gradle.tooling.internal.adapter.ProtocolToModelAdapter;
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
import org.gradle.tooling.internal.protocol.InternalPingConnection;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.File;
import java.util.List;

@SuppressWarnings("deprecation")
public class DefaultConnection implements ConnectionVersion4,
    ConfigurableConnection, InternalCancellableConnection, InternalParameterAcceptingConnection,
    StoppableConnection, InternalTestExecutionConnection, InternalPhasedActionConnection,
    InternalInvalidatableVirtualFileSystemConnection, InternalStopWhenIdleConnection,
    InternalPingConnection {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultConnection.class);

    private static final GradleVersion MIN_CLIENT_VERSION = GradleVersion.version("3.0");
    private ProtocolToModelAdapter adapter;
    private BuildProcessState buildProcessState;
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
        consumerVersion = GradleVersion.version(providerConnectionParameters.getConsumerVersion());
        connection.configure(providerConnectionParameters, consumerVersion);
    }

    private void assertUsingSupportedJavaVersion() {
        try {
            UnsupportedJavaRuntimeException.assertUsingSupportedDaemonVersion();
        } catch (IllegalArgumentException e) {
            LOGGER.warn(e.getMessage());
        }
    }

    private void initializeServices(File gradleUserHomeDir) {
        NativeServices.initializeOnClient(gradleUserHomeDir, NativeServicesMode.fromSystemProperties());
        ServiceRegistry loggingServices = LoggingServiceRegistry.newEmbeddableLogging();
        // Merge the connection services into the build process services
        // It would be better to separate these into different scopes, but many things still assume that connection services are available in the global scope,
        // so keep them merged as a migration step
        // It would also be better to create the build process services only if they are needed, ie when the tooling API is used in embedded mode
        buildProcessState = new BuildProcessState(true, AgentStatus.disabled(), ClassPath.EMPTY, loggingServices, NativeServices.getInstance()) {
            @Override
            protected void addProviders(ServiceRegistryBuilder builder) {
                builder.provider(new ConnectionScopeServices());
            }
        };
        adapter = buildProcessState.getServices().get(ProtocolToModelAdapter.class);
        connection = buildProcessState.getServices().get(ProviderConnection.class);
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
        buildProcessState.close();
    }

    /**
     * This is used by consumers 2.1-rc-1 and later
     */
    @Override
    public BuildResult<?> getModel(ModelIdentifier modelIdentifier, InternalCancellationToken cancellationToken, BuildParameters operationParameters) throws org.gradle.tooling.internal.protocol.BuildExceptionVersion1, InternalUnsupportedModelException, InternalUnsupportedBuildArgumentException, IllegalStateException {
        ProviderOperationParameters providerParameters = validateAndConvert(operationParameters);
        BuildCancellationToken buildCancellationToken = new InternalCancellationTokenAdapter(cancellationToken);
        Object result = connection.run(modelIdentifier.getName(), buildCancellationToken, providerParameters);
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
        Object results = connection.run(action, buildCancellationToken, providerParameters);
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
        Object results = connection.run(action, buildCancellationToken, providerParameters);
        return new ProviderBuildResult<>(Cast.uncheckedNonnullCast(results));
    }

    /**
     * This is used by consumers 4.8 and later
     */
    @Override
    public BuildResult<?> run(InternalPhasedAction phasedAction, PhasedActionResultListener listener, InternalCancellationToken cancellationToken, BuildParameters operationParameters) {
        ProviderOperationParameters providerParameters = validateAndConvert(operationParameters);
        BuildCancellationToken buildCancellationToken = new InternalCancellationTokenAdapter(cancellationToken);
        Object results = connection.runPhasedAction(phasedAction, listener, buildCancellationToken, providerParameters);
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
        Object results = connection.runTests(testExecutionRequestVersion2, buildCancellationToken, providerParameters);
        return new ProviderBuildResult<>(results);
    }

    private ProviderOperationParameters validateAndConvert(BuildParameters buildParameters) {
        LOGGER.info("Tooling API is using target Gradle version: {}.", GradleVersion.current().getVersion());
        assertUsingSupportedJavaVersion();

        checkUnsupportedTapiVersion();
        ProviderOperationParameters parameters = adapter.builder(ProviderOperationParameters.class)
            .mixInTo(ProviderOperationParameters.class, BuildLogLevelMixIn.class)
            .build(buildParameters);

        DeprecationLogger.reset();
        IncubationLogger.reset();
        return parameters;
    }

    private UnsupportedVersionException unsupportedConnectionException() {
        return new UnsupportedVersionException(String.format("Support for clients using a tooling API version older than %s was removed in Gradle 5.0. %sYou should upgrade your tooling API client to version %s or later.",
            MIN_CLIENT_VERSION.getVersion(),
            createCurrentVersionMessage(),
            MIN_CLIENT_VERSION.getVersion()));
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

    @Override
    public void notifyDaemonsAboutChangedPaths(List<String> changedPaths, BuildParameters operationParameters) {
        ProviderOperationParameters providerParameters = validateAndConvert(operationParameters);
        connection.notifyDaemonsAboutChangedPaths(changedPaths, providerParameters);
    }

    @Override
    public void stopWhenIdle(BuildParameters operationParameters) {
        ProviderOperationParameters providerParameters = validateAndConvert(operationParameters);
        connection.stopWhenIdle(providerParameters);
    }

    @Override
    public void ping(BuildParameters operationParameters) {
        ProviderOperationParameters providerParameters = validateAndConvert(operationParameters);
        connection.ping(providerParameters);
    }
}
