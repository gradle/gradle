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
        for (TestClassPattern pattern : patterns) {
            if (pattern.mayBeIncluded(fullQualifiedName)) {
                return true;
            }
        }

        return false;
    }

    public static String getSimpleName(String fullQualifiedName) {
        String simpleName = StringUtils.substringAfterLast(fullQualifiedName, ".");
        if ("".equals(simpleName)) {
            return fullQualifiedName;
        }
        return simpleName;
    }

    private abstract static class TestClassPattern {
        protected final String[] segments;
        protected final boolean exactlyMatch;

        private TestClassPattern(String pattern) {
            this.segments = getSegments(pattern);
            this.exactlyMatch = !pattern.contains("*");
        }

        protected abstract boolean mayBeIncluded(String fullQualifiedName);

        private static TestClassPattern fromPattern(String pattern) {
            char firstCharacter = pattern.charAt(0);
            if (Character.isUpperCase(firstCharacter)) {
                return new SimpleNamePattern(pattern);
            } else {
                return new FullQualifiedNamePattern(pattern);
            }
        }

        private String[] getSegments(String pattern) {
            int firstWildcardIndex = pattern.indexOf('*');
            if (firstWildcardIndex == -1) {
                return StringUtils.splitPreserveAllTokens(pattern, '.');
            }
            return StringUtils.splitPreserveAllTokens(pattern.substring(0, firstWildcardIndex), '.');
        }

    }

    private static class SimpleNamePattern extends TestClassPattern {
        private SimpleNamePattern(String pattern) {
            super(pattern);
        }

        @Override
        protected boolean mayBeIncluded(String fullQualifiedName) {
            String simpleName = getSimpleName(fullQualifiedName);
            if (exactlyMatch || segments.length > 1) {
                return simpleName.equals(segments[0]);
            } else {
                return simpleName.startsWith(segments[0]);
            }
        }
    }

    private static class FullQualifiedNamePattern extends TestClassPattern {
        private FullQualifiedNamePattern(String pattern) {
            super(pattern);
        }

        @Override
        protected boolean mayBeIncluded(String fullQualifiedName) {
            if (patternStartsWithWildcard()) {
                return true;
            }
            if (!patternStartsWithLowerCase()) {
                return false;
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

        private boolean patternStartsWithLowerCase() {
            return segments[0].length() > 0 && Character.isLowerCase(segments[0].charAt(0));
        }

        private boolean lastNameElementMatchesPenultimatePatternElement(String[] className, int index) {
            return index == segments.length - 2 && index == className.length - 1 && lastElementMatch(className[index], segments[index]);
        }

        private boolean nameElementMatchesLastPatternElement(String[] className, int index) {
            return index == segments.length - 1 && lastElementMatch(className[index], segments[index]);
        }

        private boolean lastElementMatch(String className, String patternName) {
            return exactlyMatch ? className.equals(patternName) : className.startsWith(patternName);
        }

        private boolean patternStartsWithWildcard() {
            return segments.length == 0;
        }

        private boolean classNameIsShorterThanPattern(String[] classNameArray) {
            return classNameArray.length < segments.length - 1;
        }
    }
}
