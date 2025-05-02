/*
 * Copyright 2020 the original author or authors.
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

import org.gradle.launcher.daemon.configuration.DaemonParameters;
import org.gradle.tooling.internal.provider.connection.ProviderOperationParameters;

import java.util.Map;

public class ConnectionOperationParameters {
    private final DaemonParameters daemonParameters;
    private final Map<String, String> tapiSystemProperties;
    private final ProviderOperationParameters operationParameters;

    public ConnectionOperationParameters(DaemonParameters daemonParameters, Map<String, String> tapiSystemProperties, ProviderOperationParameters operationParameters) {
        this.daemonParameters = daemonParameters;
        this.tapiSystemProperties = tapiSystemProperties;
        this.operationParameters = operationParameters;
    }

    public DaemonParameters getDaemonParameters() {
        return daemonParameters;
    }

    public Map<String, String> getTapiSystemProperties() {
        return tapiSystemProperties;
    }

    public ProviderOperationParameters getOperationParameters() {
        return operationParameters;
    }
}
