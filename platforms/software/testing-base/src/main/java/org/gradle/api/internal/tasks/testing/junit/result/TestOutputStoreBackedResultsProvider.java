/*
 * Copyright 2016 the original author or authors.
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

import org.gradle.api.tasks.testing.TestOutputEvent;

import java.io.IOException;
import java.io.Writer;

public abstract class TestOutputStoreBackedResultsProvider implements TestResultsProvider {
    protected final TestOutputStore.Reader reader;

    public TestOutputStoreBackedResultsProvider(TestOutputStore outputStore) {
        this.reader = outputStore.reader();
    }

    @Override
    public boolean hasOutput(final long classId, final TestOutputEvent.Destination destination) {
        return reader.hasOutput(classId, destination);
    }

    @Override
    public boolean hasOutput(long classId, long testId, TestOutputEvent.Destination destination) {
        return reader.hasOutput(classId, testId, destination);
    }

    @Override
    public void writeAllOutput(final long classId, final TestOutputEvent.Destination destination, final Writer writer) {
        reader.writeAllOutput(classId, destination, writer);
    }

    @Override
    public void writeNonTestOutput(final long classId, final TestOutputEvent.Destination destination, final Writer writer) {
        reader.writeNonTestOutput(classId, destination, writer);
    }

    @Override
    public void writeTestOutput(final long classId, final long testId, final TestOutputEvent.Destination destination, final Writer writer) {
        reader.writeTestOutput(classId, testId, destination, writer);
    }

    @Override
    public void close() throws IOException {
        reader.close();
    }

}
