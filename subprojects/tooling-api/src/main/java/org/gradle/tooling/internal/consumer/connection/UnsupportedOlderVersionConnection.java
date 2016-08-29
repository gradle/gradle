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

package org.gradle.tooling.internal.consumer.connection;

import org.gradle.tooling.BuildAction;
import org.gradle.tooling.UnsupportedVersionException;
import org.gradle.tooling.internal.adapter.ProtocolToModelAdapter;
import org.gradle.tooling.internal.build.VersionOnlyBuildEnvironment;
import org.gradle.tooling.internal.consumer.TestExecutionRequest;
import org.gradle.tooling.internal.consumer.parameters.ConsumerOperationParameters;
import org.gradle.tooling.internal.protocol.ConnectionMetaDataVersion1;
import org.gradle.tooling.internal.protocol.ConnectionVersion4;
import org.gradle.tooling.model.build.BuildEnvironment;
import org.gradle.tooling.model.internal.Exceptions;

/**
 * An adapter for unsupported connection using a {@code ConnectionVersion4} based provider.
 *
 * <p>Used for providers >= 1.0-milestone-3 and <= 1.0-milestone-7.</p>
 */
public class UnsupportedOlderVersionConnection implements ConsumerConnection {
    private final ProtocolToModelAdapter adapter;
    private final String version;
    private final ConnectionMetaDataVersion1 metaData;

    public UnsupportedOlderVersionConnection(ConnectionVersion4 delegate, ProtocolToModelAdapter adapter) {
        this.adapter = adapter;
        this.version = delegate.getMetaData().getVersion();
        this.metaData = delegate.getMetaData();
    }

    public void stop() {
    }

    public String getDisplayName() {
        return metaData.getDisplayName();
    }

    public <T> T run(Class<T> type, ConsumerOperationParameters operationParameters) throws UnsupportedOperationException, IllegalStateException {
        if (type.equals(BuildEnvironment.class)) {
            return adapter.adapt(type, doGetBuildEnvironment());
        }
        throw new UnsupportedVersionException(String.format("Support for builds using Gradle versions older than 1.2 was removed in tooling API version 3.0. You are currently using Gradle version %s. You should upgrade your Gradle build to use Gradle 1.2 or later.", version));
    }

    private Object doGetBuildEnvironment() {
        return new VersionOnlyBuildEnvironment(version);
    }

    public <T> T run(BuildAction<T> action, ConsumerOperationParameters operationParameters) throws UnsupportedOperationException, IllegalStateException {
        return new UnsupportedActionRunner(version).run(action, operationParameters);
    }

    public void runTests(TestExecutionRequest testExecutionRequest, ConsumerOperationParameters operationParameters) {
        throw Exceptions.unsupportedFeature(operationParameters.getEntryPointName(), version, "2.6");
    }

}
