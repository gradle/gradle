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

import org.gradle.api.internal.tasks.testing.TestClassProcessor;
import org.gradle.api.internal.tasks.testing.TestClassRunInfo;
import org.gradle.api.internal.tasks.testing.TestResultProcessor;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * In order to speed up the development feedback cycle, this class guarantee previous failed test classes
 * to be passed to its delegate first.
 */
public class RunPreviousFailedFirstTestClassProcessor implements TestClassProcessor {
    private final Set<String> previousFailedTestClasses;
    private final TestClassProcessor delegate;
    private final LinkedHashSet<TestClassRunInfo> prioritizedTestClasses = new LinkedHashSet<TestClassRunInfo>();
    private final LinkedHashSet<TestClassRunInfo> otherTestClasses = new LinkedHashSet<TestClassRunInfo>();

    public RunPreviousFailedFirstTestClassProcessor(Set<String> previousFailedTestClasses, TestClassProcessor delegate) {
        this.previousFailedTestClasses = previousFailedTestClasses;
        this.delegate = delegate;
    }

    @Override
    public void startProcessing(TestResultProcessor resultProcessor) {
        delegate.startProcessing(resultProcessor);
    }

    @Override
    public void processTestClass(TestClassRunInfo testClass) {
        if (previousFailedTestClasses.contains(testClass.getTestClassName())) {
            prioritizedTestClasses.add(testClass);
        } else {
            otherTestClasses.add(testClass);
        }
    }

    @Override
    public void stop() {
        for (TestClassRunInfo test : prioritizedTestClasses) {
            delegate.processTestClass(test);
        }
        for (TestClassRunInfo test : otherTestClasses) {
            delegate.processTestClass(test);
        }
        delegate.stop();
    }

    @Override
    public void stopNow() {
        delegate.stopNow();
    }
}
