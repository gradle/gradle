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

import org.gradle.tooling.internal.consumer.parameters.ConsumerOperationParameters;
import org.gradle.tooling.internal.consumer.versioning.VersionDetails;
import org.gradle.tooling.internal.protocol.BuildParametersVersion1;
import org.gradle.tooling.internal.protocol.ConnectionVersion4;
import org.gradle.tooling.internal.protocol.InternalConnection;
import org.gradle.tooling.internal.reflect.CompatibleIntrospector;

/**
 * An implementation that wraps a protocol instance that has rigid compatibility policy.
 * <p>
 * by Szczepan Faber, created at: 12/22/11
 */
public class AdaptedConnection implements ConsumerConnection {
    private final ConnectionVersion4 delegate;

    public AdaptedConnection(ConnectionVersion4 delegate) {
        this.delegate = delegate;
    }

    public void stop() {
        delegate.stop();
    }

    public String getDisplayName() {
        return delegate.getMetaData().getDisplayName();
    }

    public VersionDetails getVersionDetails() {
        return new VersionDetails(delegate.getMetaData().getVersion());
    }

    @SuppressWarnings({"deprecation"})
    public <T> T getModel(Class<T> type, ConsumerOperationParameters operationParameters) throws UnsupportedOperationException, IllegalStateException {
        if (delegate instanceof InternalConnection) {
            return ((InternalConnection) delegate).getTheModel(type, operationParameters);
        } else {
            return (T) delegate.getModel((Class) type, operationParameters);
        }
    }

    public void executeBuild(BuildParametersVersion1 buildParameters, ConsumerOperationParameters operationParameters) throws IllegalStateException {
        delegate.executeBuild(buildParameters, operationParameters);
    }

    public ConnectionVersion4 getDelegate() {
        return delegate;
    }

    public void configureLogging(boolean verboseLogging) {
        new CompatibleIntrospector(delegate).callSafely("configureLogging", verboseLogging);
    }
}
