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

import org.gradle.api.Action;
import org.gradle.api.tasks.testing.TestOutputEvent;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.io.Writer;

/**
 * Implementation of {@link TestResultsProvider} that holds the test results in memory. Output is still loaded from disk.
 */
public class InMemoryTestResultsProvider implements TestResultsProvider {
    @Nullable
    public static InMemoryTestResultsProvider loadFromDirectory(File resultsDir) {
        PersistentTestResult result = new TestResultSerializer(resultsDir).read(TestResultSerializer.VersionMismatchAction.THROW_EXCEPTION);
        if (result == null) {
            return null;
        }
        return new InMemoryTestResultsProvider(result, new TestOutputStore(resultsDir).reader());
    }

    private final PersistentTestResult result;
    private final TestOutputStore.Reader reader;

    public InMemoryTestResultsProvider(PersistentTestResult result, TestOutputStore.Reader reader) {
        if (result == null) {
            throw new IllegalArgumentException("result cannot be null");
        }
        if (reader == null) {
            throw new IllegalArgumentException("reader cannot be null");
        }
        this.result = result;
        this.reader = reader;
    }

    @Override
    public PersistentTestResult getResult() {
        return result;
    }

    @Override
    public void visitChildren(Action<? super TestResultsProvider> visitor) {
        for (PersistentTestResult child : result.getChildren()) {
            visitor.execute(new InMemoryTestResultsProvider(child, reader));
        }
    }

    @Override
    public boolean hasChildren() {
        return !result.getChildren().isEmpty();
    }

    @Override
    public void copyOutput(TestOutputEvent.Destination destination, Writer writer) {
        reader.copyTestOutput(result.getId(), destination, writer);
    }

    @Override
    public boolean hasOutput(TestOutputEvent.Destination destination) {
        return reader.hasOutput(result.getId(), destination);
    }

    @Override
    public void close() throws IOException {
        reader.close();
    }

}
