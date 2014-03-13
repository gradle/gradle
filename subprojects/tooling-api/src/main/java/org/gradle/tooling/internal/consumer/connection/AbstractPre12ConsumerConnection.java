/*
 * Copyright 2013 the original author or authors.
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

import org.gradle.tooling.internal.adapter.CompatibleIntrospector;
import org.gradle.tooling.internal.adapter.ProtocolToModelAdapter;
import org.gradle.tooling.internal.consumer.ConnectionParameters;
import org.gradle.tooling.internal.consumer.parameters.ConsumerOperationParameters;
import org.gradle.tooling.internal.consumer.versioning.VersionDetails;
import org.gradle.tooling.internal.protocol.ConnectionVersion4;
import org.gradle.tooling.model.internal.Exceptions;

/**
 * An adapter to a pre 1.2 provider.
 */
public abstract class AbstractPre12ConsumerConnection extends AbstractConsumerConnection {
    public AbstractPre12ConsumerConnection(ConnectionVersion4 delegate, VersionDetails providerMetaData, ProtocolToModelAdapter adapter) {
        super(delegate, providerMetaData);
    }

    @Override
    public void configure(ConnectionParameters connectionParameters) {
        new CompatibleIntrospector(getDelegate()).callSafely("configureLogging", connectionParameters.getVerboseLogging());
    }

    public <T> T run(Class<T> type, ConsumerOperationParameters operationParameters) throws UnsupportedOperationException, IllegalStateException {
        if (type.equals(Void.class)) {
            doRunBuild(operationParameters);
            return null;
        } else {
            if (operationParameters.getTasks() != null) {
                throw Exceptions.unsupportedOperationConfiguration("modelBuilder.forTasks()", getVersionDetails().getVersion());
            }
            return doGetModel(type, operationParameters);
        }
    }

    protected abstract <T> T doGetModel(Class<T> modelType, ConsumerOperationParameters operationParameters);

    protected void doRunBuild(ConsumerOperationParameters operationParameters) {
        getDelegate().executeBuild(operationParameters, operationParameters);
    }
}
