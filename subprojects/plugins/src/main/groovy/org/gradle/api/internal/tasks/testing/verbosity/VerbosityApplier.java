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

package org.gradle.api.internal.tasks.testing.verbosity;

import org.gradle.api.tasks.testing.*;
import org.slf4j.Logger;

/**
 * Test output listener that logs extra stuff based on the verbosity configuration
 * <p>
 * by Szczepan Faber, created at: 10/31/11
 */
public class VerbosityApplier implements TestOutputListener {
    private final Logger logger;
    private final TestVerbosity verbosity;

    public VerbosityApplier(Logger logger, TestVerbosity verbosity) {
        this.logger = logger;
        this.verbosity = verbosity;
    }

    public void onOutput(TestDescriptor testDescriptor, TestOutputEvent outputEvent) {
        if (verbosity.getShowStandardStream()) {
            logger.info(outputEvent.getMessage());
        }
    }
}
