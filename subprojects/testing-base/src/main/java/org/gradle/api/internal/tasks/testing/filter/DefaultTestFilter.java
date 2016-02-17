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

import com.google.common.collect.Sets;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.testing.TestFilter;

import java.util.HashSet;
import java.util.Set;

public class DefaultTestFilter implements TestFilter {

    private Set<String> testNames = new HashSet<String>();
    private boolean failOnNoMatching = true;

    private void validateName(String name) {
        if (name == null || name.length() == 0) {
            throw new InvalidUserDataException("Selected test name cannot be null or empty.");
        }
    }

    @Override
    public TestFilter includeTestsMatching(String testNamePattern) {
        validateName(testNamePattern);
        testNames.add(testNamePattern);
        return this;
    }

    @Override
    public TestFilter includeTest(String className, String methodName) {
        validateName(className);
        if(methodName == null || methodName.trim().isEmpty()){
            testNames.add(new StringBuilder(className).append(".*").toString());
        }else{
            testNames.add(new StringBuilder(className).append(".").append(methodName).toString());
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
        return testNames;
    }

    @Override
    public TestFilter setIncludePatterns(String... testNamePatterns) {
        for (String name : testNamePatterns) {
            validateName(name);
        }
        this.testNames = Sets.newHashSet(testNamePatterns);
        return this;
    }
}
