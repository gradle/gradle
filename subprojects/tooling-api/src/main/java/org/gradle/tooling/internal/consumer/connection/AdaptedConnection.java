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

import org.gradle.tooling.internal.consumer.parameters.ConsumerConnectionParameters;
import org.gradle.tooling.internal.consumer.parameters.ConsumerOperationParameters;
import org.gradle.tooling.internal.consumer.versioning.VersionDetails;
import org.gradle.tooling.internal.protocol.ConnectionVersion4;
import org.gradle.tooling.internal.adapter.CompatibleIntrospector;
import org.gradle.tooling.internal.protocol.ProjectVersion3;

/**
 * An implementation that wraps a protocol instance that has rigid compatibility policy.
 * <p>
 * by Szczepan Faber, created at: 12/22/11
 */
public class AdaptedConnection extends AbstractConsumerConnection {

    public AdaptedConnection(ConnectionVersion4 delegate, VersionDetails providerMetaData) {
        super(delegate, providerMetaData);
    }

    public void configure(ConsumerConnectionParameters connectionParameters) {
        new CompatibleIntrospector(getDelegate()).callSafely("configureLogging", connectionParameters.getVerboseLogging());
    }

    public <T> T run(Class<T> type, ConsumerOperationParameters operationParameters) throws UnsupportedOperationException, IllegalStateException {
        if (type.equals(Void.class)) {
            doRunBuild(operationParameters);
            return null;
        } else {
            return doGetModel(type, operationParameters);
        }
    }

    protected  <T> T doGetModel(Class<T> type, ConsumerOperationParameters operationParameters) {
        return (T) getDelegate().getModel(type.asSubclass(ProjectVersion3.class), operationParameters);
    }

    protected void doRunBuild(ConsumerOperationParameters operationParameters) {
        getDelegate().executeBuild(operationParameters, operationParameters);
    }
}
