/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.internal.tasks.testing.junit.result;

import org.gradle.api.Action;
import org.gradle.api.tasks.testing.TestOutputEvent;

import java.io.Writer;

public class InMemoryTestResultsProvider implements TestResultsProvider {
    private final Iterable<TestClassResult> results;
    private final PersistedTestOutput.Reader outputReader;

    public InMemoryTestResultsProvider(Iterable<TestClassResult> results, PersistedTestOutput.Reader outputReader) {
        this.results = results;
        this.outputReader = outputReader;
    }

    public boolean hasOutput(String className, TestOutputEvent.Destination destination) {
        return outputReader.hasOutput(className, destination);
    }

    public void writeOutputs(String className, TestOutputEvent.Destination destination, Writer writer) {
        outputReader.readTo(className, destination, writer);
    }

    public void writeOutputs(String className, String testCaseName, TestOutputEvent.Destination destination, Writer writer) {
        outputReader.readTo(className, testCaseName, destination, writer);
    }

    public void visitClasses(final Action<? super TestClassResult> visitor) {
        for (TestClassResult result : results) {
            visitor.execute(result);
        }
    }
}
