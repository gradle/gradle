/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.tooling.internal.consumer;

import org.gradle.tooling.BuildLauncher;
import org.gradle.tooling.internal.consumer.async.AsyncConsumerActionExecutor;

import java.net.URI;
import java.util.List;

public class DefaultBuildLauncherInternal extends DefaultBuildLauncher {
    public DefaultBuildLauncherInternal(AsyncConsumerActionExecutor connection, ConnectionParameters parameters) {
        super(connection, parameters);
    }

    /**
     * Specifies classpath URIs used for loading user-defined classes. This list is in addition to the default classpath.
     *
     * @param classpath Classpath URIs
     * @return this
     * @since 2.8
     */
    public BuildLauncher withClasspath(List<URI> classpath) {
        operationParamsBuilder.setClasspath(classpath);
        return this;
    }
}
