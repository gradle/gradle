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

import org.gradle.api.internal.tasks.testing.ClassTestDefinition;
import org.gradle.api.internal.tasks.testing.DirectoryBasedTestDefinition;
import org.gradle.api.internal.tasks.testing.TestClassProcessor;
import org.gradle.api.internal.tasks.testing.TestDefinition;
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
    private final LinkedHashSet<TestDefinition> prioritizedTestClasses = new LinkedHashSet<>();
    private final LinkedHashSet<TestDefinition> otherTestClasses = new LinkedHashSet<>();

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
    public void processTestDefinition(TestDefinition testClass) {
        if (wasPreviouslyRun(testClass)) {
            prioritizedTestClasses.add(testClass);
        } else {
            otherTestClasses.add(testClass);
        }
    }

    @Override
    public void stop() {
        for (TestDefinition test : prioritizedTestClasses) {
            delegate.processTestDefinition(test);
        }
        for (TestDefinition test : otherTestClasses) {
            delegate.processTestDefinition(test);
        }
        delegate.stop();
    }

    @Override
    public void stopNow() {
        delegate.stopNow();
    }

    private boolean wasPreviouslyRun(TestDefinition testDefinition) {
        if (testDefinition instanceof ClassTestDefinition) {
            return previousFailedTestClasses.contains(((ClassTestDefinition) testDefinition).getTestClassName());
        } else if (testDefinition instanceof DirectoryBasedTestDefinition){
            return previousFailedTestDefinitionDirectories.contains(((DirectoryBasedTestDefinition) testDefinition).getTestDefintionFile());
        } else {
            throw new IllegalStateException("Unexpected test definition type " + testDefinition.getClass().getName());
        }
    }
}
