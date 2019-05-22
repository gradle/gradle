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

import java.io.File;
import java.io.Writer;

public class BinaryResultBackedTestResultsProvider extends TestOutputStoreBackedResultsProvider {
    private final TestResultSerializer resultSerializer;

    public BinaryResultBackedTestResultsProvider(File resultsDir) {
        super(new TestOutputStore(resultsDir));
        this.resultSerializer = new TestResultSerializer(resultsDir);
    }

    @Override
    public boolean hasOutput(final long id, final TestOutputEvent.Destination destination) {
        final boolean[] hasOutput = new boolean[1];
        withReader(new ReaderAction2ReaderAction2ReaderAction2(hasOutput, id, destination));
        return hasOutput[0];
    }

    @Override
    public void writeAllOutput(final long id, final TestOutputEvent.Destination destination, final Writer writer) {
        withReader(new ReaderAction2ReaderAction2(id, destination, writer));
    }

    @Override
    public boolean isHasResults() {
        return resultSerializer.isHasResults();
    }

    @Override
    public void writeNonTestOutput(final long id, final TestOutputEvent.Destination destination, final Writer writer) {
        withReader(new ReaderAction2(id, destination, writer));
    }

    @Override
    public void writeTestOutput(final long classId, final long testId, final TestOutputEvent.Destination destination, final Writer writer) {
        withReader(new ReaderAction(classId, testId, destination, writer));
    }

    @Override
    public void visitClasses(final Action<? super TestClassResult> visitor) {
        resultSerializer.read(visitor);
    }

    private static class ReaderAction implements Action<TestOutputStore.Reader> {
        private final long classId;
        private final long testId;
        private final TestOutputEvent.Destination destination;
        private final Writer writer;

        public ReaderAction(long classId, long testId, TestOutputEvent.Destination destination, Writer writer) {
            this.classId = classId;
            this.testId = testId;
            this.destination = destination;
            this.writer = writer;
        }

        @Override
        public void execute(TestOutputStore.Reader reader) {
            reader.writeTestOutput(classId, testId, destination, writer);
        }
    }

    private static class ReaderAction2 implements Action<TestOutputStore.Reader> {
        private final long id;
        private final TestOutputEvent.Destination destination;
        private final Writer writer;

        public ReaderAction2(long id, TestOutputEvent.Destination destination, Writer writer) {
            this.id = id;
            this.destination = destination;
            this.writer = writer;
        }

        @Override
        public void execute(TestOutputStore.Reader reader) {
            reader.writeNonTestOutput(id, destination, writer);
        }
    }

    private static class ReaderAction2ReaderAction2 implements Action<TestOutputStore.Reader> {
        private final long id;
        private final TestOutputEvent.Destination destination;
        private final Writer writer;

        public ReaderAction2ReaderAction2(long id, TestOutputEvent.Destination destination, Writer writer) {
            this.id = id;
            this.destination = destination;
            this.writer = writer;
        }

        @Override
        public void execute(TestOutputStore.Reader reader) {
            reader.writeAllOutput(id, destination, writer);
        }
    }

    private static class ReaderAction2ReaderAction2ReaderAction2 implements Action<TestOutputStore.Reader> {
        private final boolean[] hasOutput;
        private final long id;
        private final TestOutputEvent.Destination destination;

        public ReaderAction2ReaderAction2ReaderAction2(boolean[] hasOutput, long id, TestOutputEvent.Destination destination) {
            this.hasOutput = hasOutput;
            this.id = id;
            this.destination = destination;
        }

        @Override
        public void execute(TestOutputStore.Reader reader) {
            hasOutput[0] = reader.hasOutput(id, destination);
        }
    }
}
