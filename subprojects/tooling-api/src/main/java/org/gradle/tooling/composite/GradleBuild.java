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

package org.gradle.tooling.composite;

import org.gradle.api.Incubating;

import java.io.File;
import java.net.URI;

/**
 * Represents a participant in a composite.
 *
 */
@Incubating
public interface GradleBuild {
    /**
     * Builds a new Gradle composite participant.
     */
    @Incubating
    interface Builder {

        /**
         * Sets the root project directory for this Gradle Build.
         *
         * @param projectDir root project directory.
         * @return this
         */
        Builder forProjectDirectory(File projectDir);

        /**
         * Specifies the Gradle distribution described in the build should be used.
         *
         * @return this
         */
        Builder useBuildDistribution();

        /**
         * Specifies the Gradle distribution to use.
         *
         * @param gradleHome The Gradle installation directory.
         * @return this
         */
        Builder useInstallation(File gradleHome);

        /**
         * Specifies the version of Gradle to use.
         *
         * @param gradleVersion The version to use.
         * @return this
         */
        Builder useGradleVersion(String gradleVersion);

        /**
         * Specifies the Gradle distribution to use.
         *
         * @param gradleDistribution The distribution to use.
         *
         * @return this
         */
        Builder useDistribution(URI gradleDistribution);

        /**
         * Creates an immutable GradleBuild instance based on this builder.
         *
         * @return a new instance, never null.
         */
        GradleBuild create();
    }

    /**
     * Build Identity to be used to correlate results.
     *
     * @return this build's identity, never null
     */
    BuildIdentity toBuildIdentity();

    /**
     * Project Identity to be used to correlate results.
     *
     * @param projectPath path to project in a Gradle build (e.g., :foo:bar)
     * @return identity for a project in this build with the given path
     */
    ProjectIdentity toProjectIdentity(String projectPath);
}
