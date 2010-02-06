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

import org.gradle.api.tasks.testing.TestListener;
import org.gradle.api.testing.TestClassProcessor;
import org.gradle.api.testing.fabric.TestClassRunInfo;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

public abstract class AbstractBatchTestClassProcessor implements TestClassProcessor {
    private final Set<String> testClassFileNames = new HashSet<String>();
    private TestListener testListener;

    public void processTestClass(TestClassRunInfo testClass) {
        testClassFileNames.add(testClass.getTestClassName().replace('.', File.separatorChar) + ".class");
    }

    public Set<String> getTestClassFileNames() {
        return testClassFileNames;
    }

    public TestListener getTestListener() {
        return testListener;
    }

    public void startProcessing(TestListener listener) {
        this.testListener = listener;
    }

    public void endProcessing() {
        if (testClassFileNames.isEmpty()) {
            return;
        }
        executeTests();
    }

    protected abstract void executeTests();
}
