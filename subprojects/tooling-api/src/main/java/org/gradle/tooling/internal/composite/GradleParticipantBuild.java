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

package org.gradle.tooling.internal.composite;

import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.ProjectConnection;
import org.gradle.tooling.composite.ProjectIdentity;
import org.gradle.tooling.internal.consumer.DefaultGradleConnector;

import java.io.File;

class GradleParticipantBuild {
    private final GradleBuildInternal build;
    private final File gradleUserHome;
    private final File projectDirectory;
    private final File daemonBaseDir;

    public GradleParticipantBuild(GradleBuildInternal build) {
        this(build, null);
    }

    public GradleParticipantBuild(GradleBuildInternal build, File gradleUserHome) {
        this(build, gradleUserHome, build.getProjectDir(), null);
    }

    private GradleParticipantBuild(GradleBuildInternal build, File gradleUserHome, File projectDirectory, File daemonBaseDir) {
        this.build = build;
        this.gradleUserHome = gradleUserHome;
        this.projectDirectory = projectDirectory;
        this.daemonBaseDir = daemonBaseDir;
    }

    public GradleParticipantBuild withDaemonBaseDir(File daemonBaseDir) {
        return new GradleParticipantBuild(build, gradleUserHome, projectDirectory, daemonBaseDir);
    }

    public GradleParticipantBuild withProjectDirectory(File projectDirectory) {
        return new GradleParticipantBuild(build, gradleUserHome, projectDirectory, daemonBaseDir);
    }

    public boolean isRoot() {
        return build.getProjectDir().equals(projectDirectory);
    }

    public ProjectIdentity toProjectIdentity(String projectPath) {
        return build.toProjectIdentity(projectPath);
    }

    public ProjectConnection connect() {
        return connector().forProjectDirectory(projectDirectory).connect();
    }

    private GradleConnector connector() {
        DefaultGradleConnector connector = (DefaultGradleConnector) GradleConnector.newConnector();
        connector.useGradleUserHomeDir(gradleUserHome);
        if (daemonBaseDir != null) {
            connector.daemonBaseDir(daemonBaseDir);
        }
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
