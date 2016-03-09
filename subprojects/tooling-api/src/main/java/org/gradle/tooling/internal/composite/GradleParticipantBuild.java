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

import java.io.File;

class GradleParticipantBuild {
    private final GradleBuildInternal build;
    private final File projectDirectory;

    public GradleParticipantBuild(GradleBuildInternal build) {
        this(build, build.getProjectDir());
    }

    public GradleParticipantBuild(GradleBuildInternal build, File projectDirectory) {
        this.build = build;
        this.projectDirectory = projectDirectory;
    }

    public ProjectConnection connect() {
        return connector().forProjectDirectory(projectDirectory).connect();
    }

    private GradleConnector connector() {
        GradleConnector connector = GradleConnector.newConnector();
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

        return connector;
    }

}
