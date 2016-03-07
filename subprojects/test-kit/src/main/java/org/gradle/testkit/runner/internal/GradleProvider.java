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

package org.gradle.testkit.runner.internal;

import org.gradle.testkit.runner.GradleRunner;
import org.gradle.tooling.GradleConnector;

import java.io.File;
import java.net.URI;

public abstract class GradleProvider {

    private GradleProvider() {
    }

    public abstract void applyTo(GradleConnector gradleConnector);

    public abstract void applyTo(GradleRunner gradleRunner);

    public static GradleProvider installation(File gradleHome) {
        return new InstallationGradleProvider(gradleHome);
    }

    public static GradleProvider uri(URI location) {
        return new UriGradleProvider(location);
    }

    public static GradleProvider version(String version) {
        return new VersionGradleProvider(version);
    }

    private static final class InstallationGradleProvider extends GradleProvider {
        private final File gradleHome;

        private InstallationGradleProvider(File gradleHome) {
            this.gradleHome = gradleHome;
        }

        @Override
        public void applyTo(GradleConnector gradleConnector) {
            gradleConnector.useInstallation(gradleHome);
        }

        @Override
        public void applyTo(GradleRunner gradleRunner) {
            gradleRunner.withGradleInstallation(gradleHome);
        }
    }

    private static final class UriGradleProvider extends GradleProvider {
        private final URI uri;

        public UriGradleProvider(URI uri) {
            this.uri = uri;
        }

        @Override
        public void applyTo(GradleConnector gradleConnector) {
            gradleConnector.useDistribution(uri);
        }

        @Override
        public void applyTo(GradleRunner gradleRunner) {
            gradleRunner.withGradleDistribution(uri);
        }
    }

    private static final class VersionGradleProvider extends GradleProvider {
        private final String gradleVersion;

        public VersionGradleProvider(String gradleVersion) {
            this.gradleVersion = gradleVersion;
        }

        @Override
        public void applyTo(GradleConnector gradleConnector) {
            gradleConnector.useGradleVersion(gradleVersion);
        }

        @Override
        public void applyTo(GradleRunner gradleRunner) {
            gradleRunner.withGradleVersion(gradleVersion);
        }
    }
}
