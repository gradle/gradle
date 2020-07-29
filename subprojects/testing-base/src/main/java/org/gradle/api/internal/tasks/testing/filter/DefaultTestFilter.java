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

import org.gradle.api.InvalidUserDataException;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.testing.TestFilter;
import org.gradle.internal.scan.UsedByScanPlugin;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

@UsedByScanPlugin("test-distribution")
public class DefaultTestFilter implements TestFilter {

    private final Set<String> includeTestNames = new HashSet<String>();
    private final Set<String> excludeTestNames = new HashSet<String>();
    private final Set<String> commandLineIncludeTestNames = new HashSet<String>();
    private boolean failOnNoMatching = true;

    private void validateName(String name) {
        if (name == null || name.length() == 0) {
            throw new InvalidUserDataException("Selected test name cannot be null or empty.");
        }
    }

    @Override
    public TestFilter includeTestsMatching(String testNamePattern) {
        validateName(testNamePattern);
        includeTestNames.add(testNamePattern);
        return this;
    }

    @Override
    public TestFilter excludeTestsMatching(String testNamePattern) {
        validateName(testNamePattern);
        excludeTestNames.add(testNamePattern);
        return this;
    }

    @Override
    public TestFilter includeTest(String className, String methodName) {
        return addToFilteringSet(includeTestNames, className, methodName);
    }

    @Override
    public TestFilter excludeTest(String className, String methodName) {
        return addToFilteringSet(excludeTestNames, className, methodName);
    }

    private TestFilter addToFilteringSet(Set<String> filter, String className, String methodName) {
        validateName(className);
        if (methodName == null || methodName.trim().isEmpty()) {
            filter.add(className + ".*");
        } else {
            filter.add(className + "." + methodName);
        }
        return this;
    }

    @Override
    public void setFailOnNoMatchingTests(boolean failOnNoMatchingTests) {
        this.failOnNoMatching = failOnNoMatchingTests;
    }

    @Override
    public boolean isFailOnNoMatchingTests() {
        return failOnNoMatching;
    }

    @Override
    @Input
    public Set<String> getIncludePatterns() {
        return includeTestNames;
    }

    @Override
    public Set<String> getExcludePatterns() {
        return excludeTestNames;
    }

    @Override
    public TestFilter setIncludePatterns(String... testNamePatterns) {
        return setFilteringPatterns(includeTestNames, testNamePatterns);
    }

    @Override
    public TestFilter setExcludePatterns(String... testNamePatterns) {
        return setFilteringPatterns(excludeTestNames, testNamePatterns);
    }

    private TestFilter setFilteringPatterns(Set<String> filter, String... testNamePatterns) {
        for (String name : testNamePatterns) {
            validateName(name);
        }
        filter.clear();
        filter.addAll(Arrays.asList(testNamePatterns));
        return this;
    }

    @Input
    @UsedByScanPlugin("test-distribution")
    public Set<String> getCommandLineIncludePatterns() {
        return commandLineIncludeTestNames;
    }

    public TestFilter setCommandLineIncludePatterns(Collection<String> testNamePatterns) {
        for (String name : testNamePatterns) {
            validateName(name);
        }
        this.commandLineIncludeTestNames.clear();
        this.commandLineIncludeTestNames.addAll(testNamePatterns);
        return this;
    }
}
