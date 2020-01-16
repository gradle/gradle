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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * This processor uses test class duration information to resort test classes by
 * duration in descending order before passing them on for processing.
 *
 * Tests processed without duration information are at the end behind known classes.
 * If no durations at all are known beforehand, this will not change the test order.
 */
public class RunSortedBySizeTestClassProcessor implements TestClassProcessor {
    private final TestClassProcessor delegate;
    private final Map<String, Long> durationByTestClassName;

    private final List<TestClassRunInfo> knownTestClasses = new ArrayList<TestClassRunInfo>();
    private final List<TestClassRunInfo> unknownTestClasses = new ArrayList<TestClassRunInfo>();

    public RunSortedBySizeTestClassProcessor(Map<String, Long> durationByTestClassName, TestClassProcessor delegate) {
        this.durationByTestClassName = durationByTestClassName;
        this.delegate = delegate;
    }

    @Override
    public void startProcessing(TestResultProcessor resultProcessor) {
        delegate.startProcessing(resultProcessor);
    }

    @Override
    public void processTestClass(TestClassRunInfo testClass) {
        if (durationByTestClassName.containsKey(testClass.getTestClassName())) {
            knownTestClasses.add(testClass);
        } else {
            unknownTestClasses.add(testClass);
        }
    }

    @Override
    public void stop() {
        // sort and process known classes first
        Collections.sort(knownTestClasses, new Comparator<TestClassRunInfo>() {
            @Override
            public int compare(TestClassRunInfo t1, TestClassRunInfo t2) {
                // Reverse the argument order for descending sort order:
                return durationByTestClassName.get(t2.getTestClassName())
                    .compareTo(durationByTestClassName.get(t1.getTestClassName()));
            }
        });
        for (TestClassRunInfo testClassRunInfo : knownTestClasses) {
            delegate.processTestClass(testClassRunInfo);
        }
        for (TestClassRunInfo testClassRunInfo : unknownTestClasses) {
            delegate.processTestClass(testClassRunInfo);
        }
        delegate.stop();
    }

    @Override
    public void stopNow() {
        delegate.stopNow();
    }
}
