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

package org.gradle.api.internal.tasks.testing.processors;

import org.gradle.api.internal.tasks.testing.DefaultTestClassRunInfo;
import org.gradle.api.internal.tasks.testing.ResourceBasedTestClassRunInfo;
import org.gradle.api.internal.tasks.testing.TestClassProcessor;
import org.gradle.api.internal.tasks.testing.TestClassRunInfo;
import org.gradle.api.internal.tasks.testing.TestResultProcessor;

import java.io.File;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * In order to speed up the development feedback cycle, this class guarantee previous failed test classes
 * to be passed to its delegate first.
 */
public class RunPreviousFailedFirstTestClassProcessor implements TestClassProcessor {
    private final Set<String> previousFailedTestClasses;
    private final Set<File> previousFailedTestDefinitionDirectories;
    private final TestClassProcessor delegate;
    private final LinkedHashSet<TestClassRunInfo> prioritizedTestClasses = new LinkedHashSet<TestClassRunInfo>();
    private final LinkedHashSet<TestClassRunInfo> otherTestClasses = new LinkedHashSet<TestClassRunInfo>();

    public RunPreviousFailedFirstTestClassProcessor(Set<String> previousFailedTestClasses, Set<File> previousFailedTestDefinitionDirectories, TestClassProcessor delegate) {
        this.previousFailedTestClasses = previousFailedTestClasses;
        this.previousFailedTestDefinitionDirectories = previousFailedTestDefinitionDirectories;
        this.delegate = delegate;
    }

    @Override
    public void startProcessing(TestResultProcessor resultProcessor) {
        delegate.startProcessing(resultProcessor);
    }

    @Override
    public void processTestDefinition(TestClassRunInfo testClass) {
        if (wasPreviouslyRun(testClass)) {
            prioritizedTestClasses.add(testClass);
        } else {
            otherTestClasses.add(testClass);
        }
    }

    @Override
    public void stop() {
        for (TestClassRunInfo test : prioritizedTestClasses) {
            delegate.processTestDefinition(test);
        }
        for (TestClassRunInfo test : otherTestClasses) {
            delegate.processTestDefinition(test);
        }
        delegate.stop();
    }

    @Override
    public void stopNow() {
        delegate.stopNow();
    }

    private boolean wasPreviouslyRun(TestClassRunInfo testDefinition) {
        if (testDefinition instanceof DefaultTestClassRunInfo) {
            return previousFailedTestClasses.contains(((DefaultTestClassRunInfo) testDefinition).getTestClassName());
        } else if (testDefinition instanceof ResourceBasedTestClassRunInfo){
            return previousFailedTestDefinitionDirectories.contains(((ResourceBasedTestClassRunInfo) testDefinition).getTestDefintionFile());
        } else {
            throw new IllegalStateException("Unexpected test definition type " + testDefinition.getClass().getName());
        }
    }
}
