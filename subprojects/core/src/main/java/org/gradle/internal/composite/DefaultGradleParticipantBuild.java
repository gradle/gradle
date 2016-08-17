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

package org.gradle.internal.composite;

import java.io.File;
import java.io.Serializable;
import java.net.URI;

public class DefaultGradleParticipantBuild implements GradleParticipantBuild, Serializable {
    private final File projectDir;
    private final File gradleHome;
    private final URI gradleDistribution;
    private final String gradleVersion;

    public DefaultGradleParticipantBuild(GradleParticipantBuild build) {
        this(build.getProjectDir() != null ? new File(build.getProjectDir().getAbsolutePath()) : null, build.getGradleHome() != null ? new File(build.getGradleHome().getAbsolutePath()) : null, build.getGradleDistribution(), build.getGradleVersion());
    }

    public DefaultGradleParticipantBuild(File projectDir, File gradleHome, URI gradleDistribution, String gradleVersion) {
        this.projectDir = projectDir;
        this.gradleHome = gradleHome;
        this.gradleDistribution = gradleDistribution;
        this.gradleVersion = gradleVersion;
    }

    public File getProjectDir() {
        return projectDir;
    }

    public File getGradleHome() {
        return gradleHome;
    }

    public URI getGradleDistribution() {
        return gradleDistribution;
    }

    public String getGradleVersion() {
        return gradleVersion;
    }

    @Override
    public String toString() {
        return String.format("connectionParticipant[rootDir=%s]", projectDir.getPath());
    }
}
