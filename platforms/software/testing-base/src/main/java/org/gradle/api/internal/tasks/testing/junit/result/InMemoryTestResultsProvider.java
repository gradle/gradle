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
        PersistentTestResultTree tree = new TestResultSerializer(resultsDir).read(TestResultSerializer.VersionMismatchAction.THROW_EXCEPTION);
        if (tree == null) {
            return null;
        }
        return new InMemoryTestResultsProvider(tree, new TestOutputStore(resultsDir).reader());
    }

    private final PersistentTestResultTree tree;
    private final TestOutputStore.Reader reader;

    public InMemoryTestResultsProvider(PersistentTestResultTree tree, TestOutputStore.Reader reader) {
        if (tree == null) {
            throw new IllegalArgumentException("tree cannot be null");
        }
        if (reader == null) {
            throw new IllegalArgumentException("reader cannot be null");
        }
        this.tree = tree;
        this.reader = reader;
    }

    @Override
    public PersistentTestResult getResult() {
        return tree.getResult();
    }

    @Override
    public void visitChildren(Action<? super TestResultsProvider> visitor) {
        for (PersistentTestResultTree child : tree.getChildren()) {
            visitor.execute(new InMemoryTestResultsProvider(child, reader));
        }
    }

    @Override
    public boolean hasChildren() {
        return !tree.getChildren().isEmpty();
    }

    @Override
    public void copyOutput(TestOutputEvent.Destination destination, Writer writer) {
        reader.copyOutput(tree.getId(), destination, writer);
    }

    @Override
    public boolean hasOutput(TestOutputEvent.Destination destination) {
        return reader.hasOutput(tree.getId(), destination);
    }

    @Override
    public boolean hasAllOutput(TestOutputEvent.Destination destination) {
        if (hasOutput(destination)) {
            return true;
        }

        for (PersistentTestResultTree child : tree.getChildren()) {
            if (new InMemoryTestResultsProvider(child, reader).hasAllOutput(destination)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void close() throws IOException {
        reader.close();
    }

}
