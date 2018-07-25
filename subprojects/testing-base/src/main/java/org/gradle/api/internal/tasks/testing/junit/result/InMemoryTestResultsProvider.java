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

public class InMemoryTestResultsProvider extends TestOutputStoreBackedResultsProvider {
    private final Iterable<TestClassResult> results;

    public InMemoryTestResultsProvider(Iterable<TestClassResult> results, TestOutputStore outputStore) {
        super(outputStore);
        this.results = results;
    }

    @Override
    public boolean hasOutput(final long id, final TestOutputEvent.Destination destination) {
        final boolean[] hasOutput = new boolean[1];
        withReader(new Action<TestOutputStore.Reader>() {
            @Override
            public void execute(TestOutputStore.Reader reader) {
                hasOutput[0] = reader.hasOutput(id, destination);
            }
        });
        return hasOutput[0];
    }

    @Override
    public void writeAllOutput(final long id, final TestOutputEvent.Destination destination, final Writer writer) {
        withReader(new Action<TestOutputStore.Reader>() {
            @Override
            public void execute(TestOutputStore.Reader reader) {
                reader.writeAllOutput(id, destination, writer);
            }
        });
    }

    @Override
    public void writeNonTestOutput(final long id, final TestOutputEvent.Destination destination, final Writer writer) {
        withReader(new Action<TestOutputStore.Reader>() {
            @Override
            public void execute(TestOutputStore.Reader reader) {
                reader.writeNonTestOutput(id, destination, writer);
            }
        });
    }

    @Override
    public void writeTestOutput(final long classId, final long testId, final TestOutputEvent.Destination destination, final Writer writer) {
        withReader(new Action<TestOutputStore.Reader>() {
            @Override
            public void execute(TestOutputStore.Reader reader) {
                reader.writeTestOutput(classId, testId, destination, writer);
            }
        });
    }

    @Override
    public void visitClasses(final Action<? super TestClassResult> visitor) {
        for (TestClassResult result : results) {
            visitor.execute(result);
        }
    }

    @Override
    public boolean isHasResults() {
        return results.iterator().hasNext();
    }
}
