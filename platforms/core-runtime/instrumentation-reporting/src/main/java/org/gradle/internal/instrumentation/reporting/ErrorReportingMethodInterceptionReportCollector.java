/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.internal.instrumentation.reporting;

import java.io.File;

/**
 * An implementation of {@link MethodInterceptionReportCollector} that throws an exception if a report file is generated.
 */
public class ErrorReportingMethodInterceptionReportCollector implements MethodInterceptionReportCollector {

    @Override
    public void collect(File report) {
        if (report.exists()) {
            throw new IllegalStateException("Report file should not be generated, but was: " + report);
        }
    }
}
