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

public abstract class AbstractConnectionParameters implements ConnectionParameters {
    private final File gradleUserHomeDir;
    private final Boolean embedded;
    private final Integer daemonMaxIdleTimeValue;
    private final TimeUnit daemonMaxIdleTimeUnits;
    private final File daemonBaseDir;
    private final boolean verboseLogging;

    public static class Builder {
        protected File gradleUserHomeDir;
        protected Boolean embedded;
        protected Integer daemonMaxIdleTimeValue;
        protected TimeUnit daemonMaxIdleTimeUnits;
        protected boolean verboseLogging;
        protected File daemonBaseDir;

        protected Builder() {
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

        public void setDaemonBaseDir(File daemonBaseDir) {
            this.daemonBaseDir = daemonBaseDir;
        }
    }

    protected AbstractConnectionParameters(File gradleUserHomeDir, Boolean embedded,
                                           Integer daemonMaxIdleTimeValue, TimeUnit daemonMaxIdleTimeUnits, File daemonBaseDir,
                                           boolean verboseLogging) {
        this.gradleUserHomeDir = gradleUserHomeDir;
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
