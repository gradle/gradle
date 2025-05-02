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

package org.gradle.launcher.cli;

import org.gradle.api.internal.StartParameterInternal;
import org.gradle.launcher.configuration.AllProperties;
import org.gradle.launcher.daemon.configuration.DaemonParameters;

public class Parameters {
    private final StartParameterInternal startParameter;
    private final DaemonParameters daemonParameters;
    private final AllProperties properties;

    public Parameters(StartParameterInternal startParameter, DaemonParameters daemonParameters, AllProperties properties) {
        this.startParameter = startParameter;
        this.daemonParameters = daemonParameters;
        this.properties = properties;
    }

    public AllProperties getProperties() {
        return properties;
    }

    public DaemonParameters getDaemonParameters() {
        return daemonParameters;
    }

    public StartParameterInternal getStartParameter() {
        return startParameter;
    }
}
