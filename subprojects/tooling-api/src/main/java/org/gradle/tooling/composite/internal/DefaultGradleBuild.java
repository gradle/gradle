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

import org.gradle.tooling.composite.BuildIdentity;
import org.gradle.tooling.composite.ProjectIdentity;

import java.io.File;
import java.net.URI;

public class DefaultGradleBuild implements GradleBuildInternal {
    private final File projectDir;
    private final File gradleHome;
    private final URI gradleDistribution;
    private final String gradleVersion;

    public DefaultGradleBuild(File projectDir, File gradleHome, URI gradleDistribution, String gradleVersion) {
        this.projectDir = projectDir;
        this.gradleHome = gradleHome;
        this.gradleDistribution = gradleDistribution;
        this.gradleVersion = gradleVersion;
    }

    @Override
    public BuildIdentity toBuildIdentity() {
        return null;
    }

    @Override
    public ProjectIdentity toProjectIdentity(String projectPath) {
        return null;
    }

    @Override
    public File getProjectDir() {
        return projectDir;
    }

    @Override
    public File getGradleHome() {
        return gradleHome;
    }

    @Override
    public URI getGradleDistribution() {
        return gradleDistribution;
    }

    @Override
    public String getGradleVersion() {
        return gradleVersion;
    }

    @Override
    public String getDisplayName() {
        return "build " + projectDir.getAbsolutePath();
    }

    @Override
    public String toString() {
        return getDisplayName();
    }
}
