/*
 * Copyright 2010 the original author or authors.
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

package org.gradle.api.internal.tasks.testing.results;

import org.gradle.api.internal.tasks.testing.TestCompleteEvent;
import org.gradle.api.internal.tasks.testing.TestDescriptorInternal;
import org.gradle.api.internal.tasks.testing.TestStartEvent;
import org.gradle.api.tasks.testing.TestListener;
import org.gradle.api.tasks.testing.TestOutputEvent;
import org.gradle.api.tasks.testing.TestOutputListener;
import org.gradle.api.tasks.testing.TestResult;

public class TestListenerAdapter implements TestListenerInternal {
    private final TestListener testListener;
    private final TestOutputListener testOutputListener;

    public TestListenerAdapter(TestListener testListener, TestOutputListener testOutputListener) {
        this.testListener = testListener;
        this.testOutputListener = testOutputListener;
    }

    @Override
    public void started(TestDescriptorInternal test, TestStartEvent startEvent) {
        if (test.isComposite()) {
            testListener.beforeSuite(test);
        } else {
            testListener.beforeTest(test);
        }
    }

    @Override
    public void completed(TestDescriptorInternal test, TestResult result, TestCompleteEvent completeEvent) {
        if (test.isComposite()) {
            testListener.afterSuite(test, result);
        } else {
            testListener.afterTest(test, result);
        }
    }

    @Override
    public void output(TestDescriptorInternal test, TestOutputEvent event) {
        testOutputListener.onOutput(test, event);
    }
}
