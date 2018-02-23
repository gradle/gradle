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

import org.gradle.tooling.internal.consumer.ConnectionParameters;
import org.gradle.tooling.internal.consumer.versioning.VersionDetails;
import org.gradle.tooling.internal.protocol.ConfigurableConnection;
import org.gradle.tooling.internal.protocol.ConnectionVersion4;

/**
 * An adapter for a Gradle 1.2 or later provider.
 */
public abstract class AbstractPost12ConsumerConnection extends AbstractConsumerConnection {
    private final ConfigurableConnection configurableConnection;

    protected AbstractPost12ConsumerConnection(ConnectionVersion4 delegate, VersionDetails providerMetaData) {
        super(delegate, providerMetaData);
        configurableConnection = (ConfigurableConnection) delegate;
    }

    @Override
    public void configure(ConnectionParameters connectionParameters) {
        configurableConnection.configure(connectionParameters);
    }
}
