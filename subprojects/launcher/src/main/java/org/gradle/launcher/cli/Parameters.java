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

import org.gradle.StartParameter;
import org.gradle.api.internal.StartParameterInternal;
import org.gradle.initialization.BuildLayoutParameters;
import org.gradle.launcher.daemon.configuration.DaemonParameters;

public class Parameters {
    private BuildLayoutParameters layout;
    private StartParameter startParameter;
    private DaemonParameters daemonParameters;

    public Parameters() {
        this.layout = new BuildLayoutParameters();
        this.startParameter = new StartParameterInternal();
        this.daemonParameters = new DaemonParameters(layout);
    }

    public Parameters(StartParameter startParameter) {
        this();
        this.startParameter = startParameter;
    }

    public DaemonParameters getDaemonParameters() {
        return daemonParameters;
    }

    public StartParameter getStartParameter() {
        return startParameter;
    }

    public BuildLayoutParameters getLayout() {
        return layout;
    }

    public void setDaemonParameters(DaemonParameters daemonParameters) {
        this.daemonParameters = daemonParameters;
    }
}
