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
package org.gradle.external.testng;

import org.gradle.api.testing.fabric.AbstractTestProcessor;
import org.gradle.api.testing.fabric.TestClassProcessResult;
import org.gradle.api.testing.fabric.TestClassRunInfo;
import org.gradle.api.testing.fabric.TestProcessResultFactory;

/**
 * @author Tom Eyckmans
 */
public class TestNGTestProcessor extends AbstractTestProcessor {
    protected TestNGTestProcessor(ClassLoader sandboxClassLoader, TestProcessResultFactory testProcessResultFactory) {
        super(sandboxClassLoader, testProcessResultFactory);
    }

    public TestClassProcessResult process(TestClassRunInfo testClassRunInfo) {
        TestClassProcessResult testProcessResult = null;

        // TODO

        return testProcessResult;
    }
}
