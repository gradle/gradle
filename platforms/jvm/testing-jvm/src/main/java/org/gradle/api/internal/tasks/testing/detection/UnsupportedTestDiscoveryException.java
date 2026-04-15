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

package org.gradle.api.internal.tasks.testing.detection;

import org.gradle.api.GradleException;
import org.gradle.internal.exceptions.ResolutionProvider;
import org.jspecify.annotations.NullMarked;

import java.util.Arrays;
import java.util.List;

/**
 * Thrown when daemon-side test discovery is enabled for a test framework that does not support it.
 * <p>
 * Daemon-side test discovery requires JUnit Platform's {@code Launcher.discover()} API, which is
 * only available when the test task is configured with {@code useJUnitPlatform()}. Other frameworks
 * (JUnit 4 standalone, TestNG) do not provide an equivalent discovery API.
 */
@NullMarked
public final class UnsupportedTestDiscoveryException extends GradleException implements ResolutionProvider {
    private final String taskPath;

    public UnsupportedTestDiscoveryException(String taskPath) {
        super("Daemon-side test discovery is only supported with JUnit Platform, but task '" + taskPath + "' is not configured to use it.");
        this.taskPath = taskPath;
    }

    @Override
    public List<String> getResolutions() {
        var taskName = extractTaskName();
        return Arrays.asList(
            "Configure JUnit Platform: testing.suites." + taskName + " { useJUnitJupiter() }",
            "Disable daemon-side test discovery: testing.suites." + taskName + " { useDaemonSideTestDiscovery = false }"
        );
    }

    private String extractTaskName() {
        int lastColon = taskPath.lastIndexOf(':');
        return lastColon >= 0 ? taskPath.substring(lastColon + 1) : taskPath;
    }
}
