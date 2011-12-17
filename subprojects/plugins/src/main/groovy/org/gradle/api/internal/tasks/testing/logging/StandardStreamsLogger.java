/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.internal.tasks.testing.logging;

import org.apache.commons.lang.StringUtils;
import org.gradle.api.tasks.testing.TestDescriptor;
import org.gradle.api.tasks.testing.TestLogging;
import org.gradle.api.tasks.testing.TestOutputEvent;
import org.gradle.api.tasks.testing.TestOutputListener;
import org.slf4j.Logger;

/**
 * Test output listener that logs extra stuff based on the logging configuration
 * <p>
 * by Szczepan Faber, created at: 10/31/11
 */
public class StandardStreamsLogger implements TestOutputListener {
    private final Logger logger;
    private final TestLogging logging;
    private TestDescriptor currentTestDescriptor;

    public StandardStreamsLogger(Logger logger, TestLogging logging) {
        this.logger = logger;
        this.logging = logging;
    }

    public void onOutput(TestDescriptor testDescriptor, TestOutputEvent outputEvent) {
        if (logging.getShowStandardStreams()) {
            if (!testDescriptor.equals(currentTestDescriptor)) {
                currentTestDescriptor = testDescriptor;
                logger.info(StringUtils.capitalize(testDescriptor.toString() + " output:"));
            }
            if (outputEvent.getDestination() == TestOutputEvent.Destination.StdOut) {
                logger.info(outputEvent.getMessage());
            } else if (outputEvent.getDestination() == TestOutputEvent.Destination.StdErr) {
                logger.error(outputEvent.getMessage());
            }
        }
    }
}
