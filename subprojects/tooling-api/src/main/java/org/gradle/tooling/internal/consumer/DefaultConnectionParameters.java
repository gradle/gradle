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
    private File gradleUserHomeDir;
    private File projectDir;
    private Boolean searchUpwards;
    private Boolean embedded;
    private Integer daemonMaxIdleTimeValue;
    private TimeUnit daemonMaxIdleTimeUnits;
    private boolean verboseLogging;

    public File getGradleUserHomeDir() {
        return gradleUserHomeDir;
    }

    public void setGradleUserHomeDir(File gradleUserHomeDir) {
        this.gradleUserHomeDir = gradleUserHomeDir;
    }

    public File getProjectDir() {
        return projectDir;
    }

    public void setProjectDir(File projectDir) {
        this.projectDir = projectDir;
    }

    public Boolean isSearchUpwards() {
        return searchUpwards;
    }

    public void setSearchUpwards(Boolean searchUpwards) {
        this.searchUpwards = searchUpwards;
    }

    public Boolean isEmbedded() {
        return embedded;
    }

    public void setEmbedded(Boolean embedded) {
        this.embedded = embedded;
    }

    public Integer getDaemonMaxIdleTimeValue() {
        return daemonMaxIdleTimeValue;
    }

    public void setDaemonMaxIdleTimeValue(Integer daemonMaxIdleTimeValue) {
        this.daemonMaxIdleTimeValue = daemonMaxIdleTimeValue;
    }

    public TimeUnit getDaemonMaxIdleTimeUnits() {
        return daemonMaxIdleTimeUnits;
    }

    public void setDaemonMaxIdleTimeUnits(TimeUnit daemonMaxIdleTimeUnits) {
        this.daemonMaxIdleTimeUnits = daemonMaxIdleTimeUnits;
    }

    public boolean getVerboseLogging() {
        return verboseLogging;
    }

    public void setVerboseLogging(boolean verboseLogging) {
        this.verboseLogging = verboseLogging;
    }
}
