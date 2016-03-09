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

import com.google.common.collect.Sets;
import org.gradle.tooling.GradleConnectionException;
import org.gradle.tooling.composite.GradleBuild;
import org.gradle.tooling.composite.GradleConnection;

import java.io.File;
import java.net.URI;
import java.util.Set;

public class ToolingClientGradleConnectionBuilder implements GradleConnection.Builder {
    private final Set<GradleBuildInternal> participants = Sets.newLinkedHashSet();
    private File gradleUserHomeDir;

    @Override
    public GradleConnection.Builder useGradleUserHomeDir(File gradleUserHomeDir) {
        this.gradleUserHomeDir = gradleUserHomeDir;
        return this;
    }

    @Override
    public GradleConnection.Builder addBuilds(GradleBuild... gradleBuilds) {
        for (GradleBuild gradleBuild : gradleBuilds) {
            addBuild(gradleBuild);
        }
        return this;
    }

    @Override
    public GradleConnection.Builder addBuild(GradleBuild gradleBuild) {
        if (gradleBuild==null) {
            throw new NullPointerException("gradleBuild must not be null");
        }
        if (!(gradleBuild instanceof GradleBuildInternal)) {
            throw new IllegalArgumentException("GradleBuild has an internal API that must be implemented.");
        }
        participants.add((GradleBuildInternal)gradleBuild);
        return this;
    }

    @Override
    public GradleConnection build() throws GradleConnectionException {
        if (participants.isEmpty()) {
            throw new IllegalStateException("At least one participant must be specified before creating a connection.");
        }

        return new ToolingClientGradleConnection(participants, gradleUserHomeDir);
    }

    @Override
    public GradleConnection.Builder useInstallation(File gradleHome) {
        throw new UnsupportedOperationException();
    }

    @Override
    public GradleConnection.Builder useDistribution(URI gradleDistribution) {
        throw new UnsupportedOperationException();
    }

    @Override
    public GradleConnection.Builder useGradleVersion(String gradleVersion) {
        throw new UnsupportedOperationException();
    }
}
