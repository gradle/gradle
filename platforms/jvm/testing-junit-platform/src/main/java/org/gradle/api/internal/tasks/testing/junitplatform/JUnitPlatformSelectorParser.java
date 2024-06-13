/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.api.internal.tasks.testing.junitplatform;

import org.junit.platform.engine.DiscoverySelector;
import org.junit.platform.engine.discovery.DiscoverySelectors;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Will be replaced by JUnit Platform's {@link org.junit.platform.engine.discovery.DiscoverySelectors#parseAll(Collection <String>)} once 5.11 is released
 * PR https://github.com/junit-team/junit5/pull/3737
 */
public class JUnitPlatformSelectorParser {
    public static List<DiscoverySelector> parse(List<String> selectorPatterns) {
        return JUnitPlatformSelectorParser.parseSelectors(selectorPatterns);
    }

    private static List<DiscoverySelector> parseSelectors(List<String> selectorPatterns) {
        return selectorPatterns.stream().flatMap(JUnitPlatformSelectorParser::parseSelector).collect(Collectors.toList());
    }

    private static Stream<DiscoverySelector> parseSelector(String selectorPattern) {
        // A subset of the supported selectors added in https://github.com/junit-team/junit5/pull/3737
        // for demonstration purposes
        String[] parts = selectorPattern.split(":", 2);
        if (parts.length != 2) {
            return Stream.empty();
        }
        switch (parts[0]) {
            case "class":
                return Stream.of(DiscoverySelectors.selectClass(parts[1]));
            case "file":
                return Stream.of(DiscoverySelectors.selectFile(parts[1]));
            case "method":
                return Stream.of(DiscoverySelectors.selectMethod(parts[1]));
            case "module":
                return Stream.of(DiscoverySelectors.selectModule(parts[1]));
            case "package":
                return Stream.of(DiscoverySelectors.selectPackage(parts[1]));
            case "uid":
                return Stream.of(DiscoverySelectors.selectUniqueId(parts[1]));
            default:
                return Stream.empty();
        }
    }
}
