/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.testkit.runner.fixtures

import org.gradle.util.GradleVersion

import java.lang.annotation.Annotation

enum FeatureCompatibility {
    PLUGIN_CLASSPATH_INJECTION(PluginClasspathInjection, '2.8'),
    CAPTURE_BUILD_OUTPUT_IN_DEBUG(CaptureBuildOutputInDebug, '2.9')

    /**
     * The minimum Gradle version usable for TestKit is 2.5 due to the use of {@link org.gradle.tooling.events.ProgressListener}.
     */
    public static final GradleVersion TESTKIT_MIN_SUPPORTED_VERSION = GradleVersion.version('2.5')

    private final Class<? extends Annotation> feature
    private final GradleVersion since

    private FeatureCompatibility(Class<? extends Annotation> feature, String since) {
        this.feature = feature
        this.since = GradleVersion.version(since)
    }

    static GradleVersion getMinSupportedVersion(Class<? extends Annotation> feature) {
        FeatureCompatibility featureCompatibility = values().find { it.feature == feature }

        if (!feature) {
            throw new IllegalArgumentException("Unsupported feature annotation '$feature'")
        }

        featureCompatibility.since
    }

    static boolean isSupported(Class<? extends Annotation> feature, GradleVersion version) {
        isValidVersion(version, getMinSupportedVersion(feature))
    }

    static boolean isValidVersion(GradleVersion comparedVersion, GradleVersion minVersion) {
        comparedVersion.compareTo(minVersion) >= 0
    }
}