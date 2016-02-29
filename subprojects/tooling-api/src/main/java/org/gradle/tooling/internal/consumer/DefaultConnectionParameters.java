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

import org.gradle.util.GradleVersion;

import java.io.File;
import java.util.concurrent.TimeUnit;

public class DefaultConnectionParameters implements ConnectionParameters {
    private final File gradleUserHomeDir;
    private final File projectDir;
    private final Boolean searchUpwards;
    private final Boolean embedded;
    private final Integer daemonMaxIdleTimeValue;
    private final TimeUnit daemonMaxIdleTimeUnits;
    private final File daemonBaseDir;
    private final boolean verboseLogging;

    public static Builder builder() {
        return new Builder();
    }

    public static Builder builder(ConnectionParameters connectionParameters) {
        return new Builder().setDaemonMaxIdleTimeUnits(connectionParameters.getDaemonMaxIdleTimeUnits()).
                setDaemonMaxIdleTimeValue(connectionParameters.getDaemonMaxIdleTimeValue()).
                setEmbedded(connectionParameters.isEmbedded()).
                setGradleUserHomeDir(connectionParameters.getGradleUserHomeDir()).
                setProjectDir(connectionParameters.getProjectDir()).
                setSearchUpwards(connectionParameters.isSearchUpwards()).
                setVerboseLogging(connectionParameters.getVerboseLogging());
    }

    public static class Builder {
        private File gradleUserHomeDir;
        private File projectDir;
        private Boolean searchUpwards;
        private Boolean embedded;
        private Integer daemonMaxIdleTimeValue;
        private TimeUnit daemonMaxIdleTimeUnits;
        private boolean verboseLogging;
        private File daemonBaseDir;

        private Builder() {
        }

        public Builder setGradleUserHomeDir(File gradleUserHomeDir) {
            this.gradleUserHomeDir = gradleUserHomeDir;
            return this;
        }

        public Builder setProjectDir(File projectDir) {
            this.projectDir = projectDir;
            return this;
        }

        public Builder setSearchUpwards(Boolean searchUpwards) {
            this.searchUpwards = searchUpwards;
            return this;
        }

        public Builder setEmbedded(Boolean embedded) {
            this.embedded = embedded;
            return this;
        }

        public Builder setDaemonMaxIdleTimeValue(Integer daemonMaxIdleTimeValue) {
            this.daemonMaxIdleTimeValue = daemonMaxIdleTimeValue;
            return this;
        }

        public Builder setDaemonMaxIdleTimeUnits(TimeUnit daemonMaxIdleTimeUnits) {
            this.daemonMaxIdleTimeUnits = daemonMaxIdleTimeUnits;
            return this;
        }

        public Builder setVerboseLogging(boolean verboseLogging) {
            this.verboseLogging = verboseLogging;
            return this;
        }

        public DefaultConnectionParameters build() {
            return new DefaultConnectionParameters(gradleUserHomeDir, projectDir, searchUpwards, embedded,
                    daemonMaxIdleTimeValue, daemonMaxIdleTimeUnits, daemonBaseDir, verboseLogging);
        }

        public void setDaemonBaseDir(File daemonBaseDir) {
            this.daemonBaseDir = daemonBaseDir;
        }
    }

    private DefaultConnectionParameters(File gradleUserHomeDir, File projectDir, Boolean searchUpwards, Boolean embedded,
                                        Integer daemonMaxIdleTimeValue, TimeUnit daemonMaxIdleTimeUnits, File daemonBaseDir,
                                        boolean verboseLogging) {
        this.gradleUserHomeDir = gradleUserHomeDir;
        this.projectDir = projectDir;
        this.searchUpwards = searchUpwards;
        this.embedded = embedded;
        this.daemonMaxIdleTimeValue = daemonMaxIdleTimeValue;
        this.daemonMaxIdleTimeUnits = daemonMaxIdleTimeUnits;
        this.daemonBaseDir = daemonBaseDir;
        this.verboseLogging = verboseLogging;
    }

    public File getDaemonBaseDir() {
        return daemonBaseDir;
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

    public String getConsumerVersion() {
        return GradleVersion.current().getVersion();
    }

    public boolean getVerboseLogging() {
        return verboseLogging;
    }
}
