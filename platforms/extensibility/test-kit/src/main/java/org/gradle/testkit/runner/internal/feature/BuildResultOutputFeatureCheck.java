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

package org.gradle.testkit.runner.internal.feature;

import org.gradle.internal.deprecation.DeprecationLogger;
import org.gradle.testkit.runner.UnsupportedFeatureException;
import org.gradle.util.GradleVersion;

import static org.gradle.tooling.internal.consumer.DefaultGradleConnector.MINIMUM_SUPPORTED_GRADLE_VERSION;

public class BuildResultOutputFeatureCheck implements FeatureCheck {

    private final GradleVersion targetGradleVersion;
    private final boolean embedded;

    public BuildResultOutputFeatureCheck(GradleVersion targetGradleVersion, boolean embedded) {
        this.targetGradleVersion = targetGradleVersion;
        this.embedded = embedded;
    }

    @Override
    public void verify() {
        if (!supportsVersion() && embedded) {
            throw new UnsupportedFeatureException("capture build output in debug mode with the GradleRunner", targetGradleVersion, TestKitFeature.CAPTURE_BUILD_RESULT_OUTPUT_IN_DEBUG.getSince());
        } else {
            if (targetGradleVersion.compareTo(MINIMUM_SUPPORTED_GRADLE_VERSION) < 0) {
                DeprecationLogger.deprecate("Capturing build output in debug mode with the GradleRunner for the version of Gradle you are using (%s) is deprecated with TestKit. " +
                        "TestKit will only support the last 5 major versions in future.")
                    .willBecomeAnErrorInGradle9()
                    .withUserManual("third_party_integration", "sec:embedding_compatibility");
            }
        }
    }

    private boolean supportsVersion() {
        return targetGradleVersion.compareTo(TestKitFeature.CAPTURE_BUILD_RESULT_OUTPUT_IN_DEBUG.getSince()) >= 0;
    }
}
