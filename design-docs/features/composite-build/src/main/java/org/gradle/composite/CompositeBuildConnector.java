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

package org.gradle.composite;

import java.io.File;
import java.net.URI;

/**
 * Get one of these using `GradleConnector#newComposite` (or something like that)
 */
public interface CompositeBuildConnector {

    // Define the Gradle distribution to use for coordinating the composite
    // TODO: Maybe find a better way to specify the Gradle distribution, and reuse the same mechanism to define the distribution for a participant
    CompositeBuildConnector useInstallation(File gradleHome);
    CompositeBuildConnector useGradleVersion(String gradleVersion);
    CompositeBuildConnector useDistribution(URI gradleDistribution);

    // Define the Gradle user home directory for the entire composite
    // TODO: Do we need to permit this per-participant as well?
    CompositeBuildConnector useGradleUserHomeDir(File gradleUserHomeDir);

    Participant addParticipant(File rootProjectDirectory);

    CompositeBuildConnection connect();

    interface Participant {
        Participant useInstallation(File gradleHome);
        Participant useGradleVersion(String gradleVersion);
        Participant useDistribution(URI gradleDistribution);
    }
}
