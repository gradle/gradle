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

public class TestClassSelectionMatcher {
    private final List<TestClassPattern> patterns = new ArrayList<TestClassPattern>();

    public TestClassSelectionMatcher(Collection<String> includedTests, Collection<String> includedTestsCommandLine) {
        for (String pattern : includedTests) {
            patterns.add(new TestClassPattern(pattern));
        }
        for (String pattern : includedTestsCommandLine) {
            patterns.add(new TestClassPattern(pattern));
        }
    }

    public boolean maybeMatchClass(String fullQualifiedName) {
        if (patterns.isEmpty()) {
            return true;
        }
        String simpleName = getSimpleName(fullQualifiedName);
        for (TestClassPattern pattern : patterns) {
            if (pattern.maybeMatchClass(fullQualifiedName, simpleName)) {
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

    private static class TestClassPattern {
        private String[] segments; // org.gradle.FooTest.testMethod -> ["org","gradle","FooTest", "testMethod"]
        private String[] segmentsBeforeWildcard; // org.gradle.FooTest.test* -> ["org","gradle","FooTest", "test"]

        private TestClassPattern(String pattern) {
            int firstWildcardIndex = pattern.indexOf('*');
            if (firstWildcardIndex == -1) {
                segments = StringUtils.splitPreserveAllTokens(pattern, '.');
            } else {
                segmentsBeforeWildcard = StringUtils.splitPreserveAllTokens(pattern.substring(0, firstWildcardIndex), '.');
            }
        }

        private boolean maybeMatchClass(String fullQualifiedName, String simpleName) {
            if (containsWildcard()) {
                return maybeMatchClassWhenWildcard(fullQualifiedName, simpleName);
            } else {
                return maybeMatchClassWhenNoWildcard(fullQualifiedName, simpleName);
            }
        }

        private boolean maybeMatchClassWhenWildcard(String fullQualifiedName, String simpleName) {
            if (patternStartsWithWildcard()) {
                return true;
            }
            String[] classNameArray = fullQualifiedName.split("\\.");
            return segmentsEqualAndLastElementMatch(classNameArray, 1)
                || segmentsEqualAndLastElementMatch(classNameArray, 2);
        }

        private boolean maybeMatchClassWhenNoWildcard(String fullQualifiedName, String simpleName) {
            if (segments.length == 1) {
                return simpleName.equals(segments[0]);
            } else if (segments.length == 2) {
                return simpleName.equals(lastElement(segments))
                    || simpleName.equals(secondLastElement(segments));
            } else {
                String[] targetClassNameArray = fullQualifiedName.split("\\.");
                return arrayEquals(segments, segments.length, targetClassNameArray, targetClassNameArray.length)
                    || arrayEquals(segments, segments.length - 1, targetClassNameArray, targetClassNameArray.length);
            }
        }

        private boolean containsWildcard() {
            return segmentsBeforeWildcard != null;
        }

        private boolean segmentsEqualAndLastElementMatch(String[] classNameSegments, int lastElementsCount) {
            int length = segmentsBeforeWildcard.length;
            return arrayEquals(segmentsBeforeWildcard, length - lastElementsCount, classNameSegments, classNameSegments.length - 1)
                && lastElement(classNameSegments).startsWith(segmentsBeforeWildcard[length - lastElementsCount]);
        }

        private String lastElement(String[] array) {
            return array[array.length - 1];
        }

        private String secondLastElement(String[] array) {
            return array[array.length - 2];
        }

        private boolean patternStartsWithWildcard() {
            return segmentsBeforeWildcard.length == 0;
        }


        private boolean arrayEquals(String[] array1, int array1EndIndex, String[] array2, int array2EndIndex) {
            if (array1EndIndex != array2EndIndex || array1EndIndex < 0) {
                return false;
            }

            for (int i = 0; i < array1EndIndex; ++i) {
                if (!array1[i].equals(array2[i])) {
                    return false;
                }
            }
            return true;
        }
    }

}
