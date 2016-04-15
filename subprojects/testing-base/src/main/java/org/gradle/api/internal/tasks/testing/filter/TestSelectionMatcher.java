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
import java.util.List;
import java.util.regex.Pattern;

public class TestSelectionMatcher {

    private final List<Pattern> includePatterns;

    public TestSelectionMatcher(Collection<String> includedTests) {
        includePatterns = new ArrayList<Pattern>(includedTests.size());
        for (String includedTest : includedTests) {
            includePatterns.add(preparePattern(includedTest));
        }
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
        String fullName = className + "." + methodName;
        for (Pattern pattern : includePatterns) {
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