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

@Incubating
public interface GradleBuild {
    interface Builder {

        Builder forProjectDirectory(File projectDir);

        /**
         * Adds a Gradle build as a participant in a composite.
         *
         * Defaults to a project-specific Gradle version.
         *
         *
         * @return this
         */
        Builder useBuildDistribution();

        /**
         * Adds a Gradle build as a participant in a composite, specifying the Gradle distribution to use.
         *
         * @param gradleHome The Gradle installation directory.
         * @return this
         */
        Builder useInstallation(File gradleHome);

        /**
         * Adds a Gradle build as a participant in a composite, specifying the version of Gradle to use.
         *
         * @param gradleVersion The version to use.
         * @return this
         */
        Builder useGradleVersion(String gradleVersion);

        /**
         * Adds a Gradle build as a participant in a composite, specifying the Gradle distribution to use.
         *
         * @param gradleDistribution The distribution to use.
         *
         * @return this
         */
        Builder useDistribution(URI gradleDistribution);

        GradleBuild create();
    }
    BuildIdentity toBuildIdentity();
    ProjectIdentity toProjectIdentity(String projectPath);
}
