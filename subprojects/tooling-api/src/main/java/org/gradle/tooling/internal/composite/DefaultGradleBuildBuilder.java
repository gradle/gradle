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

import org.gradle.tooling.composite.GradleBuild;

import java.io.File;
import java.net.URI;

public class DefaultGradleBuildBuilder implements GradleBuild.Builder {
    private File projectDir;
    private File gradleHome;
    private URI gradleDistribution;
    private String gradleVersion;

    @Override
    public GradleBuild.Builder forProjectDirectory(File projectDir) {
        this.projectDir = projectDir;
        return this;
    }

    @Override
    public GradleBuild.Builder useBuildDistribution() {
        resetDistribution();
        return this;
    }

    @Override
    public GradleBuild.Builder useInstallation(File gradleHome) {
        resetDistribution();
        this.gradleHome = gradleHome;
        return this;
    }

    @Override
    public GradleBuild.Builder useGradleVersion(String gradleVersion) {
        resetDistribution();
        this.gradleVersion = gradleVersion;
        return this;
    }

    @Override
    public GradleBuild.Builder useDistribution(URI gradleDistribution) {
        resetDistribution();
        this.gradleDistribution = gradleDistribution;
        return this;
    }

    @Override
    public GradleBuildInternal create() {
        if (projectDir == null) {
            throw new IllegalArgumentException("Project directory must be defined for a participant.");
        }
        return new DefaultGradleBuild(projectDir, gradleHome, gradleDistribution, gradleVersion);
    }

    private void resetDistribution() {
        this.gradleHome = null;
        this.gradleDistribution = null;
        this.gradleVersion = null;
    }
}
