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

package org.gradle.tooling.internal.connection;

import org.gradle.internal.composite.GradleParticipantBuild;
import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.ProjectConnection;
import org.gradle.tooling.model.BuildIdentifier;
import org.gradle.tooling.model.ProjectIdentifier;
import org.gradle.tooling.internal.consumer.DefaultGradleConnector;

import java.io.File;
import java.util.concurrent.TimeUnit;

class ParticipantConnector {
    private final GradleParticipantBuild build;
    private final File gradleUserHome;
    private final File daemonBaseDir;
    private final Integer daemonMaxIdleTimeValue;
    private final TimeUnit daemonMaxIdleTimeUnits;

    public ParticipantConnector(GradleParticipantBuild build, File gradleUserHome, File daemonBaseDir, Integer daemonMaxIdleTimeValue, TimeUnit daemonMaxIdleTimeUnits) {
        this.build = build;
        this.gradleUserHome = gradleUserHome;
        this.daemonBaseDir = daemonBaseDir;
        this.daemonMaxIdleTimeValue = daemonMaxIdleTimeValue;
        this.daemonMaxIdleTimeUnits = daemonMaxIdleTimeUnits;
    }

    public BuildIdentifier toBuildIdentifier() {
        return new DefaultBuildIdentifier(build.getProjectDir());
    }

    public ProjectIdentifier toProjectIdentifier(String projectPath) {
        return new DefaultProjectIdentifier(build.getProjectDir(), projectPath);
    }

    public ProjectConnection connect() {
        return connector().forProjectDirectory(build.getProjectDir()).connect();
    }

    private GradleConnector connector() {
        DefaultGradleConnector connector = (DefaultGradleConnector) GradleConnector.newConnector();
        connector.useGradleUserHomeDir(gradleUserHome);
        if (daemonBaseDir != null) {
            connector.daemonBaseDir(daemonBaseDir);
        }
        if (daemonMaxIdleTimeValue != null) {
            connector.daemonMaxIdleTime(daemonMaxIdleTimeValue, daemonMaxIdleTimeUnits);
        }
        connector.searchUpwards(false);
        configureDistribution(connector);
        return connector;
    }

    private void configureDistribution(GradleConnector connector) {
        if (build.getGradleDistribution() == null) {
            if (build.getGradleHome() == null) {
                if (build.getGradleVersion() == null) {
                    connector.useBuildDistribution();
                } else {
                    connector.useGradleVersion(build.getGradleVersion());
                }
            } else {
                connector.useInstallation(build.getGradleHome());
            }
        } else {
            connector.useDistribution(build.getGradleDistribution());
        }
    }

}
