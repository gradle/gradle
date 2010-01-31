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
package org.gradle.api.testing.execution.ant;

import org.gradle.api.testing.TestClassProcessor;
import org.gradle.api.testing.fabric.TestClassRunInfo;
import org.gradle.api.testing.fabric.TestFramework;
import org.gradle.api.testing.fabric.TestFrameworkInstance;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class AntTaskBackedTestClassProcessor implements TestClassProcessor {
    private final Set<String> testClassNames = new HashSet<String>();
    private final TestFrameworkInstance<? extends TestFramework> testFrameworkInstance;

    public AntTaskBackedTestClassProcessor(TestFrameworkInstance<? extends TestFramework> testFrameworkInstance) {
        this.testFrameworkInstance = testFrameworkInstance;
    }

    public void startProcessing() {
    }

    public void processTestClass(TestClassRunInfo testClass) {
        testClassNames.add(testClass.getTestClassName().replace('.', '/') + ".class");
    }

    public void endProcessing() {
        if (testClassNames.isEmpty()) {
            return;
        }
        testFrameworkInstance.execute(testClassNames, Collections.<String>emptySet());
        testFrameworkInstance.report();
    }
}
