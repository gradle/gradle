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

public class DefaultConnectionParameters extends AbstractConnectionParameters implements ProjectConnectionParameters {
    private final File projectDir;
    private final Boolean searchUpwards;

    public static Builder builder() {
        return new Builder();
    }

    public static Builder builder(ProjectConnectionParameters connectionParameters) {
        return (Builder) new Builder().setProjectDir(connectionParameters.getProjectDir()).
            setSearchUpwards(connectionParameters.isSearchUpwards()).
            setDaemonMaxIdleTimeUnits(connectionParameters.getDaemonMaxIdleTimeUnits()).
            setDaemonMaxIdleTimeValue(connectionParameters.getDaemonMaxIdleTimeValue()).
            setEmbedded(connectionParameters.isEmbedded()).
            setGradleUserHomeDir(connectionParameters.getGradleUserHomeDir()).
            setVerboseLogging(connectionParameters.getVerboseLogging());
    }

    public static class Builder extends AbstractConnectionParameters.Builder {
        private File projectDir;
        private Boolean searchUpwards;

        private Builder() {
            super();
        }

        public Builder setProjectDir(File projectDir) {
            this.projectDir = projectDir;
            return this;
        }

        public Builder setSearchUpwards(Boolean searchUpwards) {
            this.searchUpwards = searchUpwards;
            return this;
        }

        public DefaultConnectionParameters build() {
            return new DefaultConnectionParameters(gradleUserHomeDir, projectDir, searchUpwards, embedded,
                    daemonMaxIdleTimeValue, daemonMaxIdleTimeUnits, daemonBaseDir, verboseLogging);
        }
    }

    private DefaultConnectionParameters(File gradleUserHomeDir, File projectDir, Boolean searchUpwards, Boolean embedded,
                                        Integer daemonMaxIdleTimeValue, TimeUnit daemonMaxIdleTimeUnits, File daemonBaseDir,
                                        boolean verboseLogging) {
        super(gradleUserHomeDir, embedded, daemonMaxIdleTimeValue, daemonMaxIdleTimeUnits, daemonBaseDir, verboseLogging);
        this.projectDir = projectDir;
        this.searchUpwards = searchUpwards;
    }

    public File getProjectDir() {
        return projectDir;
    }

    public Boolean isSearchUpwards() {
        return searchUpwards;
    }
}
