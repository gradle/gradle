/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.api.internal.tasks.testing.retrying;

import org.gradle.api.internal.tasks.testing.FrameworkTestClassProcessor;
import org.gradle.api.internal.tasks.testing.TestClassProcessor;
import org.gradle.api.internal.tasks.testing.TestClassRunInfo;
import org.gradle.api.internal.tasks.testing.TestResultProcessor;

import java.util.concurrent.atomic.AtomicBoolean;

public class UntilFailureTestClassProcessor implements TestClassProcessor {
    private final FrameworkTestClassProcessor processor;
    private final long untilFailureRunCount;
    private final AtomicBoolean hasAnyTestFailed;

    public UntilFailureTestClassProcessor(FrameworkTestClassProcessor processor, long untilFailureRunCount) {
        this.processor = processor;
        this.untilFailureRunCount = untilFailureRunCount;
        this.hasAnyTestFailed = new AtomicBoolean();
    }

    @Override
    public void startProcessing(TestResultProcessor resultProcessor) {
        processor.startProcessing(new UntilFailureTestResultProcessor(hasAnyTestFailed, resultProcessor));
    }

    @Override
    public void processTestClass(TestClassRunInfo testClass) {
        processor.processTestClass(testClass);
    }

    @Override
    public void stop() {
        try {
            long executions = Math.max(untilFailureRunCount, 1);
            while (shouldRetry(executions--)) {
                processor.runTests();
            }
        } finally {
            processor.stop();
        }
    }

    private boolean shouldRetry(long executions) {
        return executions > 0 && !hasAnyTestFailed.get();
    }

    @Override
    public void stopNow() {
        hasAnyTestFailed.set(true);
    }
}
