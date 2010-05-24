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

import org.gradle.api.internal.tasks.testing.*;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;

public class LoggingResultProcessor implements TestResultProcessor {
    private static final Logger LOGGER = Logging.getLogger(LoggingResultProcessor.class);
    private final String prefix;
    private final TestResultProcessor processor;

    public LoggingResultProcessor(String prefix, TestResultProcessor processor) {
        this.prefix = prefix;
        this.processor = processor;
    }

    public void started(TestDescriptorInternal test, TestStartEvent event) {
        LOGGER.lifecycle("{} START {} {}", prefix, test.getId(), test);
        processor.started(test, event);
    }

    public void failure(Object testId, Throwable result) {
        LOGGER.lifecycle("{} FAILED {}", prefix, testId);
        processor.failure(testId, result);
    }

    public void completed(Object testId, TestCompleteEvent event) {
        LOGGER.lifecycle("{} COMPLETED {} {}", prefix, testId, event.getResultType());
        processor.completed(testId, event);
    }

    public void output(Object testId, TestOutputEvent event) {
        LOGGER.lifecycle("{} OUTPUT {} {} [{}]", prefix, testId, event.getDestination(), event.getMessage());
        processor.output(testId, event);
    }
}
