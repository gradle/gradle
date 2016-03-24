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
import org.gradle.tooling.GradleConnectionException;

import java.io.File;
import java.net.URI;

/**
 * Builds a new composite Gradle connection.
 */
@Incubating
public interface GradleConnectionBuilder {
    /**
     * Specifies the user's Gradle home directory to use. Defaults to {@code ~/.gradle}.
     *
     * @param gradleUserHomeDir The user's Gradle home directory to use.
     * @return this
     */
    GradleConnectionBuilder useGradleUserHomeDir(File gradleUserHomeDir);

    /**
     * Specifies the Gradle distribution for the coordinator to use.
     *
     * @param gradleHome The Gradle installation directory.
     * @return this
     */
    GradleConnectionBuilder useInstallation(File gradleHome);

    /**
     * Specifies the version of Gradle for the coordinator to use.
     *
     * @param gradleVersion The version to use.
     * @return this
     */
    GradleConnectionBuilder useGradleVersion(String gradleVersion);

    /**
     * Specifies the Gradle distribution for the coordinator to use.
     *
     * @param gradleDistribution The distribution to use.
     *
     * @return this
     */
    GradleConnectionBuilder useDistribution(URI gradleDistribution);

    /**
     * Creates a new GradleBuildBuilder builder instance for creating Gradle composite participants.
     *
     * @param projectDirectory The root project directory for the participant.
     *
     * @return The builder. Never returns null.
     */
    GradleBuildBuilder newParticipant(File projectDirectory);

    /**
     * Builds the connection. You should call {@link GradleConnection#close()} when you are finished with the connection.
     *
     * @return The connection. Never returns null.
     * @throws GradleConnectionException If the composite is invalid (e.g., no participants).
     */
    GradleConnection build() throws GradleConnectionException;
}
