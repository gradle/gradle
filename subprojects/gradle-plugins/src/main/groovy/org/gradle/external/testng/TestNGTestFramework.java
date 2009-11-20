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

import org.gradle.api.tasks.testing.AbstractTestFramework;
import org.gradle.api.tasks.testing.AbstractTestTask;
import org.gradle.api.testing.fabric.TestFrameworkInstance;
import org.gradle.api.testing.fabric.TestProcessorFactory;
import org.gradle.api.testing.fabric.TestMethodProcessResultState;

import java.util.Map;

/**
 * @author Tom Eyckmans
 */
public class TestNGTestFramework extends AbstractTestFramework {

    public TestNGTestFramework() {
        super("testng", "TestNG");
    }

    public TestFrameworkInstance getInstance(AbstractTestTask testTask) {
        return new TestNGTestFrameworkInstance(testTask, this);
    }

    public TestProcessorFactory getProcessorFactory() {
        return new TestNGTestProcessorFactory();
    }

    public Map<TestMethodProcessResultState, TestMethodProcessResultState> getMethodProcessResultStateMapping() {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }
}
