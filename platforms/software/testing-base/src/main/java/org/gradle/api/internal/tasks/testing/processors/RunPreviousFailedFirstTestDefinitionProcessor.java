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
import org.gradle.api.internal.tasks.testing.TestDefinitionProcessor;
import org.gradle.api.internal.tasks.testing.TestDefinition;
import org.gradle.api.internal.tasks.testing.TestResultProcessor;

import java.io.File;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * In order to speed up the development feedback cycle, this class guarantee previous failed test classes
 * to be passed to its delegate first.
 */
public class RunPreviousFailedFirstTestDefinitionProcessor<D extends TestDefinition> implements TestDefinitionProcessor<D> {
    private final Set<String> previousFailedTestClasses;
    private final Set<File> previousFailedTestDefinitionDirectories;
    private final TestDefinitionProcessor<D> delegate;
    private final LinkedHashSet<D> prioritizedTestDefinitions = new LinkedHashSet<>();
    private final LinkedHashSet<D> otherTestDefinitions = new LinkedHashSet<>();

    public RunPreviousFailedFirstTestDefinitionProcessor(Set<String> previousFailedTestClasses, Set<File> previousFailedTestDefinitionDirectories, TestDefinitionProcessor<D> delegate) {
        this.previousFailedTestClasses = previousFailedTestClasses;
        this.previousFailedTestDefinitionDirectories = previousFailedTestDefinitionDirectories;
        this.delegate = delegate;
    }

    @Override
    public void startProcessing(TestResultProcessor resultProcessor) {
        delegate.startProcessing(resultProcessor);
    }

    @Override
    public void processTestDefinition(D testDefinition) {
        if (wasPreviouslyRun(testDefinition)) {
            prioritizedTestDefinitions.add(testDefinition);
        } else {
            otherTestDefinitions.add(testDefinition);
        }
    }

    @Override
    public void stop() {
        for (D test : prioritizedTestDefinitions) {
            delegate.processTestDefinition(test);
        }
        for (D test : otherTestDefinitions) {
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
            return previousFailedTestDefinitionDirectories.contains(((DirectoryBasedTestDefinition) testDefinition).getTestDefinitionsDir());
        } else {
            throw new IllegalStateException("Unexpected test definition type " + testDefinition.getClass().getName());
        }
    }
}
