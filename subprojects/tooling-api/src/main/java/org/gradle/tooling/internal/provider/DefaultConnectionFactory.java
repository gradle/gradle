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

import org.gradle.messaging.actor.internal.DefaultActorFactory;
import org.gradle.messaging.concurrent.CompositeStoppable;
import org.gradle.messaging.concurrent.DefaultExecutorFactory;
import org.gradle.tooling.internal.protocol.ConnectionFactoryVersion3;
import org.gradle.tooling.internal.protocol.ConnectionParametersVersion1;
import org.gradle.tooling.internal.protocol.ConnectionVersion3;
import org.gradle.util.GradleVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The implementation of the tooling API provider. This is loaded dynamically by the tooling API consumer, {@link org.gradle.tooling.internal.consumer.ConnectionFactory}.
 */
public class DefaultConnectionFactory implements ConnectionFactoryVersion3 {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultConnectionFactory.class);
    private final DefaultExecutorFactory executorFactory = new DefaultExecutorFactory();
    private final DefaultActorFactory actorFactory = new DefaultActorFactory(executorFactory);

    public void stop() {
        new CompositeStoppable(actorFactory, executorFactory).stop();
    }

    public ConnectionVersion3 create(ConnectionParametersVersion1 parameters) {
        LOGGER.debug("Using tooling API provider version {}.", GradleVersion.current().getVersion());

        return new DefaultConnection(parameters, actorFactory);
    }
}
