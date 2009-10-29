/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.api.testing.reporting;

import org.gradle.api.testing.execution.Pipeline;
import org.gradle.api.testing.fabric.TestClassProcessResult;

/**
 * @author Tom Eyckmans
 */
public class TestClassProcessResultReportInfo implements ReportInfo {
    private final Pipeline pipeline;
    private final TestClassProcessResult testClassProcessResult;

    public TestClassProcessResultReportInfo(Pipeline pipeline, TestClassProcessResult testClassProcessResult) {
        this.pipeline = pipeline;
        this.testClassProcessResult = testClassProcessResult;
    }

    public Pipeline getPipeline() {
        return pipeline;
    }

    public TestClassProcessResult getTestClassProcessResult() {
        return testClassProcessResult;
    }
}
