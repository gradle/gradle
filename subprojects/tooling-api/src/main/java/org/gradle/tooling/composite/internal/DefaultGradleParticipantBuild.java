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

package org.gradle.tooling.composite.internal;

import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.ProjectConnection;

import java.io.File;
import java.net.URI;

class DefaultGradleParticipantBuild implements GradleParticipantBuild {

    private final File projectDir;
    private ProjectConnection projectConnection;
    private File gradleUserHomeDir;

    private File gradleHome;
    private URI gradleDistribution;
    private String gradleVersion;

    DefaultGradleParticipantBuild(File projectDir) {
        this.projectDir = projectDir;
    }

    DefaultGradleParticipantBuild(File projectDir, File gradleHome) {
        this(projectDir);
        this.gradleHome = gradleHome;
    }

    DefaultGradleParticipantBuild(File projectDir, String gradleVersion) {
        this(projectDir);
        this.gradleVersion = gradleVersion;
    }
    DefaultGradleParticipantBuild(File projectDir, URI gradleDistribution) {
        this(projectDir);
        this.gradleDistribution = gradleDistribution;
    }

    @Override
    public File getProjectDir() {
        return projectDir;
    }

    @Override
    public ProjectConnection getConnection() {
        if (projectConnection==null) {
            projectConnection = connect();
        }
        return projectConnection;
    }

    @Override
    public String getDisplayName() {
        return "build " + projectDir.getAbsolutePath();
    }

    @Override
    public void stop() {
        if (projectConnection!=null) {
            projectConnection.close();
        }
    }

    private ProjectConnection connect() {
        return configureDistribution(GradleConnector.newConnector().
            useGradleUserHomeDir(gradleUserHomeDir).
            forProjectDirectory(getProjectDir())).
            connect();
    }

    private GradleConnector configureDistribution(GradleConnector connector) {
        if (gradleDistribution==null) {
            if (gradleHome==null) {
                if (gradleVersion==null) {
                    connector.useBuildDistribution();
                } else {
                    connector.useGradleVersion(gradleVersion);
                }
            } else {
                connector.useInstallation(gradleHome);
            }
        } else {
            connector.useDistribution(gradleDistribution);
        }

        return connector;
    }

    public void setGradleUserHomeDir(File gradleUserHomeDir) {
        this.gradleUserHomeDir = gradleUserHomeDir;
    }
}
