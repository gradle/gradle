/*
 * Copyright 2013 the original author or authors.
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
package org.gradle.api.internal.tasks.testing.filter;

import com.google.common.base.Splitter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Pattern;

public class TestSelectionMatcher {

    private final List<Pattern> includePatterns;
    private final List<Pattern> excludePatterns;

    public TestSelectionMatcher(Collection<String> includedTests) {
        this(includedTests, Collections.<String>emptyList());
    }

    public TestSelectionMatcher(Collection<String> includedTests, Collection<String> excludedTests) {
        this.includePatterns = toPatterns(includedTests);
        this.excludePatterns = toPatterns(excludedTests);
    }

    private List<Pattern> toPatterns(Collection<String> tests) {
        List<Pattern> patterns = new LinkedList<Pattern>();

        for (String test : tests) {
            patterns.add(preparePattern(test));
        }
        return patterns;
    }

    private Pattern preparePattern(String input) {
        StringBuilder pattern = new StringBuilder();
        Iterable<String> split = Splitter.on('*').split(input);
        for (String s : split) {
            if (s.equals("")) {
                pattern.append(".*"); //replace wildcard '*' with '.*'
            } else {
                if (pattern.length() > 0) {
                    pattern.append(".*"); //replace wildcard '*' with '.*'
                }
                pattern.append(Pattern.quote(s)); //quote everything else
            }
        }
        return Pattern.compile(pattern.toString());
    }

    public boolean matchesTest(String className, String methodName) {

        boolean allowedByIncludePatterns = includePatterns.isEmpty() || matchesTest(className, methodName, includePatterns);
        boolean matchedAnyExcludePatterns = !excludePatterns.isEmpty() && matchesTest(className, methodName, excludePatterns);

        return allowedByIncludePatterns && !matchedAnyExcludePatterns;
    }

    private boolean matchesTest(String className, String methodName, List<Pattern> patterns) {
        String fullName = className + "." + methodName;
        for (Pattern pattern : patterns) {
            if (methodName != null && pattern.matcher(fullName).matches()) {
                return true;
            }
            if (pattern.matcher(className).matches()) {
                return true;
            }
        }
        return false;
    }
}
