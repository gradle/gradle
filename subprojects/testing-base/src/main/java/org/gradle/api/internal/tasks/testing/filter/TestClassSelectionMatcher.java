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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.apache.commons.lang.StringUtils.splitPreserveAllTokens;
import static org.apache.commons.lang.StringUtils.substringAfterLast;

/**
 * Excludes as many classes as possible before sending them to {@link org.gradle.api.internal.tasks.testing.TestClassProcessor}
 * in order to improve test performance. Matching rules are:
 *
 * <ul>
 * <li>If the pattern starts with an upper-case letter, it's used to match simple class name.
 * <li>Otherwise, it's used to match full qualified class name.
 * </ul>
 *
 * In both cases, wildcards are supported.
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

    public static String getSimpleName(String fullQualifiedName) {
        String simpleName = substringAfterLast(fullQualifiedName, ".");
        if ("".equals(simpleName)) {
            return fullQualifiedName;
        }
        return simpleName;
    }

    private abstract static class TestClassPattern {
        protected String[] segments;

        private boolean mayBeIncluded(String fullQualifiedName, String simpleName) {
            if (patternStartsWithUpperCase()) {
                return mayBeIncluded(simpleName);
            } else {
                return mayBeIncluded(fullQualifiedName);
            }
        }

        private boolean mayBeIncluded(String className) {
            if (patternStartsWithWildcard()) {
                return true;
            }
            String[] classNameArray = className.split("\\.");
            if (classNameIsShorterThanPattern(classNameArray)) {
                return false;
            }
            for (int i = 0; i < segments.length; ++i) {
                if (lastClassNameElementMatchesPenultimatePatternElement(classNameArray, i)) {
                    return true;
                } else if (lastClassNameElementMatchesLastPatternElement(classNameArray, i)) {
                    return true;
                } else if (!classNameArray[i].equals(segments[i])) {
                    return false;
                }
            }
            return false;
        }

        private boolean lastClassNameElementMatchesPenultimatePatternElement(String[] className, int index) {
            return index == segments.length - 2 && index == className.length - 1 && className[index].equals(segments[index]);
        }

        private boolean lastClassNameElementMatchesLastPatternElement(String[] className, int index) {
            return index == segments.length - 1 && lastElementMatch(className[index], segments[index]);
        }

        protected abstract boolean lastElementMatch(String classElement, String patternElement);

        private boolean patternStartsWithWildcard() {
            return segments.length == 0;
        }

        private boolean classNameIsShorterThanPattern(String[] classNameArray) {
            return classNameArray.length < segments.length - 1;
        }

        private boolean patternStartsWithUpperCase() {
            return segments.length > 0 && segments[0].length() > 0 && Character.isUpperCase(segments[0].charAt(0));
        }

        private static TestClassPattern fromPattern(String pattern) {
            int firstWildcardIndex = pattern.indexOf('*');
            if (firstWildcardIndex == -1) {
                return new NoWildcardPattern(splitPreserveAllTokens(pattern, '.'));
            } else {
                return new WildcardPattern(splitPreserveAllTokens(pattern.substring(0, firstWildcardIndex), '.'));
            }
        }
    }

    private static class NoWildcardPattern extends TestClassPattern {
        private NoWildcardPattern(String[] segments) {
            this.segments = segments;
        }

        protected boolean lastElementMatch(String classElement, String patternElement) {
            return classElement.equals(patternElement);
        }
    }

    private static class WildcardPattern extends TestClassPattern {
        private WildcardPattern(String[] segments) {
            this.segments = segments;
        }

        @Override
        protected boolean lastElementMatch(String classElement, String patternElement) {
            return classElement.startsWith(patternElement);
        }
    }
}
