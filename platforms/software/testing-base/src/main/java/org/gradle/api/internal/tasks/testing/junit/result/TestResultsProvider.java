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

import java.io.Closeable;
import java.io.Writer;

public interface TestResultsProvider extends Closeable {
    /**
     * Get the inner result information.
     *
     * @return the result information
     */
    PersistentTestResult getResult();

    /**
     * Copies the output of just this result.
     *
     * @param destination the original destination of the output
     * @param writer the writer to copy the output to
     */
    void copyOutput(TestOutputEvent.Destination destination, Writer writer);

    /**
     * Copies the output of this result and all its children.
     *
     * @param destination the original destination of the output
     * @param writer the writer to copy the output to
     */
    default void copyAllOutput(TestOutputEvent.Destination destination, Writer writer) {
        copyOutput(destination, writer);
        visitChildren(provider -> provider.copyAllOutput(destination, writer));
    }

    /**
     * Returns true if this result has output for the given destination.
     *
     * <p>
     * Note that {@code false} does not imply that {@link #copyAllOutput(TestOutputEvent.Destination, Writer)} will not produce any output.
     * This only checks for output from this result, not any children.
     * </p>
     *
     * @param destination the destination to check for output
     * @return true if this result has output for the given destination
     */
    boolean hasOutput(TestOutputEvent.Destination destination);

    /**
     * Returns true if this result has children. Visit them with {@link #visitChildren(org.gradle.api.Action)}.
     *
     * <p>
     * This is equivalent to checking if {@code getResult() instanceof PersistentCompositeTestResult}.
     * </p>
     *
     * @return true if this result has children
     */
    boolean hasChildren();

    /**
     * Visits the children of this result provider, in no specific order. Each child is visited exactly once.
     */
    void visitChildren(Action<? super TestResultsProvider> visitor);
}
