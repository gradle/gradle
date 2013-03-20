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

import org.gradle.tooling.internal.adapter.ProtocolToModelAdapter;
import org.gradle.tooling.internal.consumer.versioning.ModelMapping;
import org.gradle.tooling.internal.protocol.*;
import org.gradle.tooling.internal.provider.connection.*;
import org.gradle.util.GradleVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultConnection implements InternalConnection, BuildActionRunner, ConfigurableConnection, ModelBuilder {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultConnection.class);
    private final ProtocolToModelAdapter adapter = new ProtocolToModelAdapter();
    private final ProviderConnection connection;

    public DefaultConnection() {
        LOGGER.debug("Tooling API provider {} created.", GradleVersion.current().getVersion());
        connection = new ProviderConnection();
    }

    /**
     * This is used by consumers 1.2-rc-1 and later.
     */
    public void configure(ConnectionParameters parameters) {
        ProviderConnectionParameters providerConnectionParameters = adapter.adapt(ProviderConnectionParameters.class, parameters);
        connection.configure(providerConnectionParameters);
    }

    /**
     * This method was used by consumers 1.0-rc-1 through to 1.1. Later consumers use {@link #configure(org.gradle.tooling.internal.protocol.ConnectionParameters)} instead.
     */
    public void configureLogging(final boolean verboseLogging) {
        ProviderConnectionParameters providerConnectionParameters = adapter.adapt(ProviderConnectionParameters.class, new VerboseLoggingOnlyConnectionParameters(verboseLogging));
        connection.configure(providerConnectionParameters);
    }

    public ConnectionMetaDataVersion1 getMetaData() {
        return new DefaultConnectionMetaData();
    }

    public void stop() {
    }

    /**
     * This is used by consumers 1.0-milestone-3 to 1.1.
     */
    @Deprecated
    public void executeBuild(BuildParametersVersion1 buildParameters, BuildOperationParametersVersion1 operationParameters) {
        logTargetVersion();
        connection.run(ModelIdentifier.NULL_MODEL, new AdaptedOperationParameters(operationParameters, buildParameters.getTasks()));
    }

    /**
     * This is used by consumers 1.0-milestone-3 to 1.0-milestone-7
     */
    @Deprecated
    public ProjectVersion3 getModel(Class<? extends ProjectVersion3> type, BuildOperationParametersVersion1 parameters) {
        return run(type, parameters);
    }

    /**
     * This is used by consumers 1.0-milestone-8 to 1.1
     */
    @Deprecated
    public <T> T getTheModel(Class<T> type, BuildOperationParametersVersion1 parameters) {
        return run(type, parameters);
    }

    private <T> T run(Class<T> type, BuildOperationParametersVersion1 parameters) {
        logTargetVersion();
        String modelName = new ModelMapping().getModelNameFromProtocolType(type);
        return (T) connection.run(modelName, new AdaptedOperationParameters(parameters));
    }

    /**
     * This is used by consumers 1.2-rc-1 to 1.5
     */
    public <T> BuildResult<T> run(Class<T> type, BuildParameters buildParameters) throws UnsupportedOperationException, IllegalStateException {
        logTargetVersion();
        ProviderOperationParameters providerParameters = adapter.adapt(ProviderOperationParameters.class, buildParameters, BuildLogLevelMixIn.class);
        String modelName = new ModelMapping().getModelNameFromProtocolType(type);
        T result = (T) connection.run(modelName, providerParameters);
        return new ProviderBuildResult<T>(result);
    }

    /**
     * This is used by consumers 1.6-rc-1 and later
     */
    public BuildResult<?> getModel(ModelIdentifier modelIdentifier, BuildParameters operationParameters) throws UnsupportedOperationException, IllegalStateException {
        logTargetVersion();
        ProviderOperationParameters providerParameters = adapter.adapt(ProviderOperationParameters.class, operationParameters, BuildLogLevelMixIn.class);
        Object result = connection.run(modelIdentifier.getName(), providerParameters);
        return new ProviderBuildResult<Object>(result);
    }

    private void logTargetVersion() {
        LOGGER.info("Tooling API uses target gradle version: {}.", GradleVersion.current().getVersion());
    }

    private static class VerboseLoggingOnlyConnectionParameters {
        private final boolean verboseLogging;

        public VerboseLoggingOnlyConnectionParameters(boolean verboseLogging) {
            this.verboseLogging = verboseLogging;
        }

        public boolean getVerboseLogging() {
            return verboseLogging;
        }
    }
}