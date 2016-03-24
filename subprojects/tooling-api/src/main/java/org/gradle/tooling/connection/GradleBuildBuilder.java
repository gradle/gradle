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

package org.gradle.tooling.connection;

import org.gradle.api.Incubating;

import java.io.File;
import java.net.URI;

/**
 * Builds a new Gradle composite participant.
 */
@Incubating
public interface GradleBuildBuilder {

    /**
     * Sets the root project directory for this Gradle Build.
     *
     * @param projectDir root project directory.
     * @return this
     */
    GradleBuildBuilder forProjectDirectory(File projectDir);

    /**
     * Specifies the Gradle distribution described in the build should be used.
     *
     * @return this
     */
    GradleBuildBuilder useBuildDistribution();

    /**
     * Specifies the Gradle distribution to use.
     *
     * @param gradleHome The Gradle installation directory.
     * @return this
     */
    GradleBuildBuilder useInstallation(File gradleHome);

    /**
     * Specifies the version of Gradle to use.
     *
     * @param gradleVersion The version to use.
     * @return this
     */
    GradleBuildBuilder useGradleVersion(String gradleVersion);

    /**
     * Specifies the Gradle distribution to use.
     *
     * @param gradleDistribution The distribution to use.
     *
     * @return this
     */
    GradleBuildBuilder useDistribution(URI gradleDistribution);

    /**
     * Creates an immutable GradleBuild instance based on this builder.
     *
     * @return a new instance, never null.
     */
    GradleBuild create();
}
