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


import org.apache.commons.lang.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;

public class TestSelectionMatcher {

    private final List<Pattern> buildScriptIncludePatterns;
    private final List<Pattern> commandLineIncludePatterns;

    public TestSelectionMatcher(Collection<String> includedTests, Collection<String> includedTestsCommandLine) {
        buildScriptIncludePatterns = preparePatternList(includedTests);
        commandLineIncludePatterns = preparePatternList(includedTestsCommandLine);
    }

    private List<Pattern> preparePatternList(Collection<String> includedTests) {
        List<Pattern> includePatterns = new ArrayList<Pattern>(includedTests.size());
        for (String includedTest : includedTests) {
            includePatterns.add(preparePattern(includedTest));
        }
        return includePatterns;
    }

    private Pattern preparePattern(String input) {
        StringBuilder pattern = new StringBuilder();
        String[] split = StringUtils.splitPreserveAllTokens(input, '*');
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
        return matchesPattern(buildScriptIncludePatterns, className, methodName)
            && matchesPattern(commandLineIncludePatterns, className, methodName);
    }

    private boolean matchesPattern(List<Pattern> includePatterns, String className, String methodName) {
        if (includePatterns.isEmpty()) {
            return true;
        }
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
