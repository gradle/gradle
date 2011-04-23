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
package org.gradle.tooling.internal.consumer;

import java.io.File;
import java.util.concurrent.TimeUnit;

public class DefaultConnectionParameters implements ConnectionParameters {
    private final File gradleUserHomeDir;
    private final File projectDir;
    private final Boolean searchUpwards;
    private final Boolean embedded;
    private final Integer daemonMaxIdleTimeValue;
    private final TimeUnit daemonMaxIdleTimeUnits;

    public DefaultConnectionParameters(File projectDir, File gradleUserHomeDir, Boolean searchUpwards, Boolean embedded, Integer daemonMaxIdleTimeValue, TimeUnit daemonMaxIdleTimeUnits) {
        this.projectDir = projectDir;
        this.gradleUserHomeDir = gradleUserHomeDir;
        this.searchUpwards = searchUpwards;
        this.embedded = embedded;
        this.daemonMaxIdleTimeValue = daemonMaxIdleTimeValue;
        this.daemonMaxIdleTimeUnits = daemonMaxIdleTimeUnits;
    }

    public File getGradleUserHomeDir() {
        return gradleUserHomeDir;
    }

    public File getProjectDir() {
        return projectDir;
    }

    public Boolean isSearchUpwards() {
        return searchUpwards;
    }

    public Boolean isEmbedded() {
        return embedded;
    }

    public Integer getDaemonMaxIdleTimeValue() {
        return daemonMaxIdleTimeValue;
    }

    public TimeUnit getDaemonMaxIdleTimeUnits() {
        return daemonMaxIdleTimeUnits;
    }
}
