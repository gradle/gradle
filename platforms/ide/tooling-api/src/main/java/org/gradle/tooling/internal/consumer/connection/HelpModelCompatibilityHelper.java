/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.tooling.internal.consumer.connection;

import org.gradle.util.GradleVersion;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

/**
 * Helper class to load the expected {@link org.gradle.tooling.model.build.Help} model output for Gradle versions &lt; 9.4.
 */
class HelpModelCompatibilityHelper {

    private HelpModelCompatibilityHelper() {
    }

    static String getRenderedText(String version) {
        GradleVersion gradleVersion = GradleVersion.version(version);
        String templateVersion = templateVersion(gradleVersion);
        String resourceName = "help-output/gradle-" + templateVersion + ".txt";

        InputStream resourceStream = Thread.currentThread().getContextClassLoader().getResourceAsStream(resourceName);
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(resourceStream, StandardCharsets.UTF_8))) {
            return reader.lines().collect(Collectors.joining(System.lineSeparator()));
        } catch (IOException e) {
            throw new RuntimeException("cannot load " + resourceName + " from classloader resource for " + version,  e);
        }
    }

    private static String templateVersion(GradleVersion gradleVersion) {
        // The classpath resources contain the unique help outputs for a range of Gradle versions.
        // For each range, the lowest version is used as the resource identifier.
        String[] helpOutputResourceVersion = {
            "9.2.0", "9.1.0", "9.0.0",
            "8.11", "8.3", "8.2", "8.1", "8.0.1", "8.0",
            "7.6", "7.5", "7.3", "7.1", "7.0",
            "6.8.1", "6.6", "6.5", "6.2", "6.1", "6.0",
            "5.0",
            "4.8", "4.6", "4.5", "4.4", "4.3", "4.0"
        };
        for (String resourceVersion : helpOutputResourceVersion) {
            if (gradleVersion.compareTo(GradleVersion.version(resourceVersion)) >= 0) {
                return resourceVersion;
            }
        }
        // We fall back to the lowest supported version for very old Gradle versions
        return "4.0";
    }
}
