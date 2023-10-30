/*
 * Copyright 2016 the original author or authors.
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

    private final File projectDir;
    private final Boolean searchUpwards;
    private final File gradleUserHomeDir;
    private final Boolean embedded;
    private final Integer daemonMaxIdleTimeValue;
    private final TimeUnit daemonMaxIdleTimeUnits;
    private final File daemonBaseDir;
    private final boolean verboseLogging;
    private final File distributionBaseDir;

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private File projectDir;
        private File gradleUserHomeDir;
        private Boolean embedded;
        private Integer daemonMaxIdleTimeValue;
        private TimeUnit daemonMaxIdleTimeUnits;
        private boolean verboseLogging;
        private File daemonBaseDir;
        private Boolean searchUpwards;
        private File distributionBaseDir;

        protected Builder() {
        }

        public Builder setProjectDir(File projectDir) {
            this.projectDir = projectDir;
            return this;
        }

        public Builder setGradleUserHomeDir(File gradleUserHomeDir) {
            this.gradleUserHomeDir = gradleUserHomeDir;
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

        public Builder setSearchUpwards(Boolean searchUpwards) {
            this.searchUpwards = searchUpwards;
            return this;
        }

        public Builder setDaemonBaseDir(File daemonBaseDir) {
            this.daemonBaseDir = daemonBaseDir;
            return this;
        }

        public void setDistributionBaseDir(File distributionBaseDir) {
            this.distributionBaseDir = distributionBaseDir;
        }

        public DefaultConnectionParameters build() {
            return new DefaultConnectionParameters(projectDir, gradleUserHomeDir, embedded, daemonMaxIdleTimeValue, daemonMaxIdleTimeUnits, daemonBaseDir, verboseLogging, searchUpwards, distributionBaseDir);
        }
    }

    private DefaultConnectionParameters(File projectDir, File gradleUserHomeDir, Boolean embedded,
                                        Integer daemonMaxIdleTimeValue, TimeUnit daemonMaxIdleTimeUnits, File daemonBaseDir,
                                        boolean verboseLogging, Boolean searchUpwards, File distributionBaseDir) {
        this.projectDir = projectDir;
        this.gradleUserHomeDir = gradleUserHomeDir;
        this.embedded = embedded;
        this.daemonMaxIdleTimeValue = daemonMaxIdleTimeValue;
        this.daemonMaxIdleTimeUnits = daemonMaxIdleTimeUnits;
        this.daemonBaseDir = daemonBaseDir;
        this.verboseLogging = verboseLogging;
        this.searchUpwards = searchUpwards;
        this.distributionBaseDir = distributionBaseDir;
    }

    @Override
    public File getProjectDir() {
        return projectDir;
    }

    @Override
    public File getDaemonBaseDir() {
        return daemonBaseDir;
    }

    @Override
    public File getGradleUserHomeDir() {
        return gradleUserHomeDir;
    }

    @Override
    public Boolean isEmbedded() {
        return embedded;
    }

    @Override
    public Integer getDaemonMaxIdleTimeValue() {
        return daemonMaxIdleTimeValue;
    }

    @Override
    public TimeUnit getDaemonMaxIdleTimeUnits() {
        return daemonMaxIdleTimeUnits;
    }

    @Override
    public String getConsumerVersion() {
        return GradleVersion.current().getVersion();
    }

    @Override
    public boolean getVerboseLogging() {
        return verboseLogging;
    }

    @Override
    public Boolean isSearchUpwards() {
        return searchUpwards;
    }

    @Override
    public File getDistributionBaseDir() {
        return distributionBaseDir;
    }
}
