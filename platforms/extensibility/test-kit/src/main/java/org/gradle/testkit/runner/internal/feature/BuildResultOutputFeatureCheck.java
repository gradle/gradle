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

import java.util.function.Function;
import java.util.logging.Logger;

import static org.gradle.tooling.internal.consumer.DefaultGradleConnector.MINIMUM_SUPPORTED_GRADLE_VERSION;

public class BuildResultOutputFeatureCheck implements FeatureCheck {

    private static final Logger LOGGER = Logger.getLogger(BuildResultOutputFeatureCheck.class.getName());

    private final GradleVersion targetGradleVersion;
    private final boolean embedded;

    public BuildResultOutputFeatureCheck(GradleVersion targetGradleVersion, boolean embedded) {
        this.targetGradleVersion = targetGradleVersion;
        this.embedded = embedded;
    }

    @Override
    public void verify() {
        if (!supportsVersion() && embedded) {
            throw new UnsupportedFeatureException(
                "capture build output in debug mode with the GradleRunner",
                targetGradleVersion,
                TestKitFeature.CAPTURE_BUILD_RESULT_OUTPUT_IN_DEBUG.getSince());
        } else {
            warnIfUnsupportedVersion(targetGradleVersion, version -> String.format("Capturing build output in debug mode with the GradleRunner for the version of Gradle you are using (%s) is deprecated with TestKit.",
                version));
        }
    }

    public static void warnIfUnsupportedVersion(GradleVersion targetGradleVersion, Function<String, String> messageSupplier) {
        if (targetGradleVersion.compareTo(MINIMUM_SUPPORTED_GRADLE_VERSION) < 0) {
            LOGGER.warning(messageSupplier.apply(targetGradleVersion.getVersion())
                + " TestKit will only support the last 5 major versions in future."
                + " This will fail with an error starting with Gradle 10."
                + " Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/tooling_api.html#sec:embedding_compatibility");
        }

        // DeprecationLogger is not normally available in this context, which is outside Gradle runtime.
        // Keep the call to DeprecationLogger in unreachable code so this deprecation can still be found by usage of DeprecationLogger and is addressed later.
        @SuppressWarnings("unused")
        Runnable keepTheDeprecationLoggerCall = () -> DeprecationLogger.deprecate("Running TestKit with unsupported old Gradle versions.")
            .willBecomeAnErrorInGradle10()
            .withUserManual("tooling_api", "sec:embedding_compatibility")
            .nagUser();
    }

    private boolean supportsVersion() {
        return targetGradleVersion.compareTo(TestKitFeature.CAPTURE_BUILD_RESULT_OUTPUT_IN_DEBUG.getSince()) >= 0;
    }
}
