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

import org.gradle.tooling.composite.BuildIdentity;
import org.gradle.tooling.composite.ProjectIdentity;
import org.gradle.tooling.internal.protocol.DefaultBuildIdentity;
import org.gradle.tooling.internal.protocol.DefaultProjectIdentity;

import java.io.File;
import java.net.URI;

public class DefaultGradleBuild implements GradleBuildInternal {
    private final File projectDir;
    private final File gradleHome;
    private final URI gradleDistribution;
    private final String gradleVersion;

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        DefaultGradleBuild that = (DefaultGradleBuild) o;

        if (!projectDir.equals(that.projectDir)) {
            return false;
        }
        if (gradleHome != null ? !gradleHome.equals(that.gradleHome) : that.gradleHome != null) {
            return false;
        }
        if (gradleDistribution != null ? !gradleDistribution.equals(that.gradleDistribution) : that.gradleDistribution != null) {
            return false;
        }
        return !(gradleVersion != null ? !gradleVersion.equals(that.gradleVersion) : that.gradleVersion != null);

    }

    @Override
    public int hashCode() {
        int result = projectDir.hashCode();
        result = 31 * result + (gradleHome != null ? gradleHome.hashCode() : 0);
        result = 31 * result + (gradleDistribution != null ? gradleDistribution.hashCode() : 0);
        result = 31 * result + (gradleVersion != null ? gradleVersion.hashCode() : 0);
        return result;
    }

    public DefaultGradleBuild(File projectDir, File gradleHome, URI gradleDistribution, String gradleVersion) {
        this.projectDir = projectDir;
        this.gradleHome = gradleHome;
        this.gradleDistribution = gradleDistribution;
        this.gradleVersion = gradleVersion;
    }

    @Override
    public BuildIdentity toBuildIdentity() {
        return new DefaultBuildIdentity(projectDir);
    }

    @Override
    public ProjectIdentity toProjectIdentity(String projectPath) {
        if (projectPath==null) {
            throw new NullPointerException("projectPath cannot be null");
        }
        if (!projectPath.startsWith(":")) {
            throw new IllegalArgumentException("projectPath must be absolute and start with a :");
        }
        return new DefaultProjectIdentity((DefaultBuildIdentity)toBuildIdentity(), projectDir, projectPath);
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
        return toBuildIdentity().toString();
    }

    @Override
    public String toString() {
        return getDisplayName();
    }
}
