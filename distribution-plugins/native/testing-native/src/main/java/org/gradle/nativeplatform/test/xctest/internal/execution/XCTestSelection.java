/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.nativeplatform.test.xctest.internal.execution;

import com.google.common.collect.ImmutableList;
import org.apache.commons.lang.StringUtils;

import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Describes the set of filtered XCTests.
 *
 * NOTE: Eventually we want to support regular Java-like test filtering, like filtering for a set of test cases
 * or test suites that match a particular pattern.  Unfortunately, XCTest is very limited with how much up-front
 * test discovery we can do and the kind of filtering we can specify from the command-line.  This class reflects
 * those limitations.
 */
public class XCTestSelection {
    public static final String INCLUDE_ALL_TESTS = "All";
    private static final String WILDCARD = "*";
    private final Set<String> includedTests = new LinkedHashSet<String>();

    public XCTestSelection(Collection<String> includedTests, Collection<String> includedTestsCommandLine) {
        Set<String> testSuiteCache = new HashSet<String>();

        prepareIncludedTestList(includedTests, testSuiteCache);
        prepareIncludedTestList(includedTestsCommandLine, testSuiteCache);

        removeLogicalDuplication(testSuiteCache);

        includeAllTestIfEmpty();
    }

    private void removeLogicalDuplication(Set<String> testSuiteCache) {
        for (Iterator<String> it = includedTests.iterator(); it.hasNext();) {
            String includedTest = it.next();
            if (isIncludedTestCase(includedTest)) {
                if (testSuiteCache.contains(getTestSuiteName(includedTest))) {
                    it.remove();
                }
            }
        }
    }

    private static boolean isIncludedTestCase(String includedTest) {
        return includedTest.contains("/");
    }

    private static String getTestSuiteName(String includedTestCase) {
        return StringUtils.split(includedTestCase, '/')[0];
    }

    private void includeAllTestIfEmpty() {
        if (includedTests.isEmpty()) {
            includedTests.add(INCLUDE_ALL_TESTS);
        }
    }

    private void prepareIncludedTestList(Collection<String> testFilters, Set<String> testSuiteCache) {
        for (String testFilter : testFilters) {
            includedTests.add(prepareIncludedTest(disallowForwardSlash(testFilter), testSuiteCache));
        }
    }

    private String disallowForwardSlash(String testFilter) {
        if (testFilter.contains("/")) {
            throw new IllegalArgumentException(String.format("'%s' is an invalid pattern. Patterns cannot contain forward slash.", testFilter));
        }
        return testFilter;
    }

    private String prepareIncludedTest(String testFilter, Set<String> testSuiteCache) {
        String[] tokens = StringUtils.splitPreserveAllTokens(testFilter, '.');
        if (tokens.length > 3) {
            throw new IllegalArgumentException(String.format("'%s' is an invalid pattern. Patterns should have one or two dots.", testFilter));
        } else if (tokens.length == 3) {
            if (WILDCARD.equals(tokens[2])) {
                String filter = tokens[0] + "." + tokens[1];
                testSuiteCache.add(filter);
                return filter;
            } else if (tokens[2].isEmpty()) {
                return testFilter;
            }
            return tokens[0] + "." + tokens[1] + "/" + tokens[2];
        } else if (tokens.length == 2 && !WILDCARD.equals(tokens[1])) {
            testSuiteCache.add(testFilter);
        }

        return testFilter;
    }

    public Collection<String> getIncludedTests() {
        return ImmutableList.copyOf(includedTests);
    }
}
