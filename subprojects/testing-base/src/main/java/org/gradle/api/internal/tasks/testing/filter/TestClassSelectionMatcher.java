/*
 * Copyright 2018 the original author or authors.
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

/**
 * Excludes as many classes as possible before sending them to {@link org.gradle.api.internal.tasks.testing.TestClassProcessor}
 * in order to improve test performance. Matching rules are:
 *
 * <ul>
 * <li>If pattern starts with a wildcard (*), we can't exclude any class. E.g., pattern '*testMethod' can match 'testMethod' in any class.</li>
 * <li>If pattern doesn't starts with a wildcard (*), we can exclude the classes that can't be matched at all. E.g., 'org.apache.Test' can be excluded by pattern 'org.gradle*'.</li>
 * <li>Two kinds of special cases without any wildcards are supported: TestClass/TestClass.testMethod can match TestClass in any package.</li>
 * </ul>
 */
public class TestClassSelectionMatcher {
    private final List<TestClassPattern> patterns = new ArrayList<TestClassPattern>();

    public TestClassSelectionMatcher(Collection<String> includedTests, Collection<String> includedTestsCommandLine) {
        for (String pattern : includedTests) {
            patterns.add(TestClassPattern.fromPattern(pattern));
        }
        for (String pattern : includedTestsCommandLine) {
            patterns.add(TestClassPattern.fromPattern(pattern));
        }
    }

    public boolean mayBeIncluded(String fullQualifiedName) {
        if (patterns.isEmpty()) {
            return true;
        }
        String simpleName = getSimpleName(fullQualifiedName);
        for (TestClassPattern pattern : patterns) {
            if (pattern.mayBeIncluded(fullQualifiedName, simpleName)) {
                return true;
            }
        }

        return false;
    }

    private String getSimpleName(String fullQualifiedName) {
        String simpleName = StringUtils.substringAfterLast(fullQualifiedName, ".");
        if ("".equals(simpleName)) {
            simpleName = fullQualifiedName;
        }
        return simpleName;
    }

    private abstract static class TestClassPattern {
        String[] segments;

        abstract boolean mayBeIncluded(String fullQualifiedName, String simpleName);

        private static TestClassPattern fromPattern(String pattern) {
            int firstWildcardIndex = pattern.indexOf('*');
            if (firstWildcardIndex == -1) {
                TestClassPattern result = new SimplePattern();
                result.segments = StringUtils.splitPreserveAllTokens(pattern, '.');
                return result;
            } else {
                TestClassPattern result = new WildcardPattern();
                result.segments = StringUtils.splitPreserveAllTokens(pattern.substring(0, firstWildcardIndex), '.');
                return result;
            }
        }
    }

    private static class SimplePattern extends TestClassPattern {
        @Override
        boolean mayBeIncluded(String fullQualifiedName, String simpleName) {
            if (segments.length == 1) {
                return simpleName.equals(segments[0]);
            } else if (segments.length == 2) {
                return simpleName.equals(segments[segments.length - 1])
                    || simpleName.equals(segments[segments.length - 2]);
            } else {
                String[] targetClassNameArray = fullQualifiedName.split("\\.");
                return patternEqualsClassName(segments.length, targetClassNameArray)
                    || patternEqualsClassName(segments.length - 1, targetClassNameArray);
            }
        }

        private boolean patternEqualsClassName(int patternArrayEndIndex, String[] targetClassNameArray) {
            if (patternArrayEndIndex != targetClassNameArray.length) {
                return false;
            }

            for (int i = 0; i < patternArrayEndIndex; ++i) {
                if (!segments[i].equals(targetClassNameArray[i])) {
                    return false;
                }
            }
            return true;
        }
    }

    private static class WildcardPattern extends TestClassPattern {
        @Override
        boolean mayBeIncluded(String fullQualifiedName, String simpleName) {
            if (patternStartsWithWildcard()) {
                return true;
            }
            String[] className = fullQualifiedName.split("\\.");
            if (classNameIsShorterThanPattern(className)) {
                return false;
            }
            for (int i = 0; i < segments.length; ++i) {
                if (lastNameElementMatchesPenultimatePatternElement(className, i)) {
                    return true;
                } else if (nameElementMatchesLastPatternElement(className, i)) {
                    return true;
                } else if (!className[i].equals(segments[i])) {
                    return false;
                }
            }
            return false;
        }

        private boolean lastNameElementMatchesPenultimatePatternElement(String[] className, int index) {
            return index == segments.length - 2 && index == className.length - 1 && className[index].startsWith(segments[index]);
        }

        private boolean nameElementMatchesLastPatternElement(String[] className, int index) {
            return index == segments.length - 1 && className[index].startsWith(segments[index]);
        }

        private boolean patternStartsWithWildcard() {
            return segments.length == 0;
        }

        private boolean classNameIsShorterThanPattern(String[] classNameArray) {
            return classNameArray.length < segments.length - 1;
        }
    }
}
