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
package org.gradle.docs.samples;

import java.util.List;

/**
 * Builds the failure message thrown when a {@code docsTest} sample fails.
 * <p>
 * The {@link #CONFIG_CACHE_EXCLUDED_MARKERS} substrings here mirror the {@code excludeTestsMatching}
 * groups in {@code platforms/documentation/docs/build.gradle.kts}: samples whose id contains any of
 * these markers are skipped when {@code enableConfigurationCacheForDocsTests=true}, so the reproduce
 * instructions for those samples must tell the user to clear that property rather than set it.
 * Keep this list in sync with {@code configCacheExcludedTestGroups} in the build script.
 */
public final class SampleFailureMessageFormatter {

    private static final List<String> CONFIG_CACHE_EXCLUDED_MARKERS = List.of(
        "snippet-optimizing-builds-configuration-cache-",
        "WithoutCC"
    );

    private static final String BANNER = "**********************************************************************************";

    private SampleFailureMessageFormatter() {
        throw new AssertionError("SampleFailureMessageFormatter is a utility class and must not be instantiated");
    }

    public static String format(String sampleId, boolean configCacheExecuter) {
        boolean excludedWhenConfigCacheEnabled = CONFIG_CACHE_EXCLUDED_MARKERS.stream().anyMatch(sampleId::contains);
        String extraParameter = (configCacheExecuter && !excludedWhenConfigCacheEnabled)
            ? " -PenableConfigurationCacheForDocsTests=true"
            : "";

        StringBuilder message = new StringBuilder();
        message.append("Sample test run failed.\n");
        message.append("To understand how docsTest works, See:\n");
        message.append("  https://github.com/gradle/gradle/blob/master/platforms/documentation/docs/README.md#testing-docs\n");
        message.append(BANNER).append('\n');
        message.append("To reproduce this failure, run:\n");
        message.append("  ./gradlew docs:docsTest --tests '*").append(sampleId).append("*'").append(extraParameter).append('\n');
        if (excludedWhenConfigCacheEnabled) {
            message.append("  Remember to set enableConfigurationCacheForDocsTests=false or the test will not be found.\n");
        }
        message.append(BANNER);
        return message.toString();
    }
}
