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
import org.gradle.tooling.internal.protocol.*;
import org.gradle.tooling.internal.provider.connection.*;
import org.gradle.util.GradleVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultConnection implements InternalConnection, BuildActionRunner, ConfigurableConnection {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultConnection.class);
    private final ProtocolToModelAdapter adapter = new ProtocolToModelAdapter();
    private final ProviderConnection connection;

    public DefaultConnection() {
        LOGGER.debug("Tooling API provider {} created.", GradleVersion.current().getVersion());
        connection = new ProviderConnection();
    }

    public void configure(ConnectionParameters parameters) {
        ProviderConnectionParameters providerConnectionParameters = adapter.adapt(ProviderConnectionParameters.class, parameters);
        connection.configure(providerConnectionParameters);
    }

    public void configureLogging(final boolean verboseLogging) {
        ProviderConnectionParameters providerConnectionParameters = adapter.adapt(ProviderConnectionParameters.class, new Object() {
            public boolean getVerboseLogging() {
                return verboseLogging;
            }
        });
        connection.configure(providerConnectionParameters);
    }

    public ConnectionMetaDataVersion1 getMetaData() {
        return new ConnectionMetaDataVersion1() {
            public String getVersion() {
                return GradleVersion.current().getVersion();
            }

            public String getDisplayName() {
                return String.format("Gradle %s", getVersion());
            }
        };
    }

    public void stop() {
    }

    @Deprecated
    public void executeBuild(BuildParametersVersion1 buildParameters, BuildOperationParametersVersion1 operationParameters) {
        logTargetVersion();
        connection.run(Void.class, new AdaptedOperationParameters(operationParameters, buildParameters.getTasks()));
    }

    @Deprecated
    public ProjectVersion3 getModel(Class<? extends ProjectVersion3> type, BuildOperationParametersVersion1 parameters) {
        logTargetVersion();
        return connection.run(type, new AdaptedOperationParameters(parameters));
    }

    @Deprecated
    public <T> T getTheModel(Class<T> type, BuildOperationParametersVersion1 parameters) {
        logTargetVersion();
        return connection.run(type, new AdaptedOperationParameters(parameters));
    }

    public <T> BuildResult<T> run(Class<T> type, BuildParameters buildParameters) throws UnsupportedOperationException, IllegalStateException {
        logTargetVersion();
        ProviderOperationParameters providerParameters = adapter.adapt(ProviderOperationParameters.class, buildParameters, BuildLogLevelMixIn.class);
        T result = connection.run(type, providerParameters);
        return new ProviderBuildResult<T>(result);
    }

    private void logTargetVersion() {
        LOGGER.info("Tooling API uses target gradle version: {}.", GradleVersion.current().getVersion());
    }
}