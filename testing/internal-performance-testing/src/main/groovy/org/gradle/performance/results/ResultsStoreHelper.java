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
package org.gradle.performance.results;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ResultsStoreHelper {
    public static final String SYSPROP_PERFORMANCE_TEST_CHANNEL = "org.gradle.performance.execution.channel";
    public static final String SYSPROP_PERFORMANCE_TEST_CHANNEL_PATTERNS = "org.gradle.performance.execution.channel.patterns";
    private static final Logger LOGGER = LoggerFactory.getLogger(ResultsStoreHelper.class);

    public static List<String> split(String string) {
        if (null != string) {
            return ImmutableList.copyOf(Splitter.on(",").split(string));
        }
        return Collections.emptyList();
    }

    public static <T extends PerformanceTestResult> WritableResultsStore<T> createResultsStoreWhenDatabaseAvailable(Supplier<WritableResultsStore<T>> supplier) {
        return PerformanceDatabase.isAvailable()
            ? supplier.get()
            : NoResultsStore.getInstance();
    }

    /**
     * MySQL doesn't support array type. So array in H2 `1,2,3` will be a string like '(1,2,3)'
     */
    public static String toArray(List<String> list) {
        return list == null ? null : "(" + String.join(",", list) + ")";
    }

    public static List<String> toList(Object[] objects) {
        return Stream.of(objects).map(Object::toString).map(String::trim).collect(Collectors.toList());
    }

    /**
     * MySQL doesn't support array type. So array in H2 `1,2,3` will be a string like '(1,2,3)'
     */
    public static List<String> toList(Object object) {
        if (object == null) {
            return null;
        }
        if (object instanceof String) {
            String str = object.toString();
            if (str.startsWith("(") && str.endsWith(")")) {
                return toList(str.substring(1, str.length() - 1).split(","));
            } else {
                return toList(str.split(","));
            }
        }
        return toList((Object[]) object);
    }

    public static String determineChannel() {
        return System.getProperty(SYSPROP_PERFORMANCE_TEST_CHANNEL, "commits");
    }

    public static OperatingSystem determineOsFromChannel() {
        String channel = determineChannel();
        int firstDashIndex = channel.indexOf('-');
        if (firstDashIndex == -1) {
            return OperatingSystem.LINUX;
        }
        String prefix = channel.startsWith("flakiness-detection")
            ? "flakiness-detection"
            : channel.substring(0, firstDashIndex);
        String channelWithoutPrefix = channel.substring(prefix.length());
        if (channelWithoutPrefix.startsWith(OperatingSystem.MAC_OS.getChannelSuffix())) {
            return OperatingSystem.MAC_OS;
        } else if (channelWithoutPrefix.startsWith(OperatingSystem.WINDOWS.getChannelSuffix())) {
            return OperatingSystem.WINDOWS;
        } else {
            return OperatingSystem.LINUX;
        }

    }

    public static List<String> determineChannelPatterns() {
        List<String> patterns = new ArrayList<>();
        patterns.add(ResultsStoreHelper.determineChannel());
        String extraPatterns = System.getProperty(SYSPROP_PERFORMANCE_TEST_CHANNEL_PATTERNS, "");
        if (!extraPatterns.isEmpty()) {
            patterns.addAll(Arrays.asList(extraPatterns.split(",")));
        }
        return patterns;
    }

    public static boolean isHistoricalChannel() {
        return determineChannel().startsWith("historical-");
    }

    public static String determineTeamCityBuildId() {
        return System.getenv("BUILD_ID");
    }

    public static boolean isAdhocPerformanceTest() {
        return "adhoc".equals(determineChannel());
    }
}
