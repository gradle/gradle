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
import java.io.IOException;
import java.io.Writer;

public interface TestResultsProvider extends Closeable {
    /**
     * Writes the output of the given test to the given writer. This method must be called only after {@link #visitClasses(org.gradle.api.Action)}.
     *
     * Writes all output for the test class.
     */
    void writeAllOutput(long id, TestOutputEvent.Destination destination, Writer writer);
    
    /**
     * Writes all output for the test class with enricher (allows to write some pre or post data)
     * 
     * @see #writeAllOutput(long, org.gradle.api.tasks.testing.TestOutputEvent.Destination, Writer)
     */
    void writeAllOutput(long id, WriterOutputEnricher enricher, Writer writer);

    void writeNonTestOutput(long id, TestOutputEvent.Destination destination, Writer writer);
    
    void writeNonTestOutput(long id, WriterOutputEnricher enricher, Writer writer);

    /**
     * Writes the output of the given test to the given writer. This method must be called only after {@link #visitClasses(org.gradle.api.Action)}.
     *
     * Write all output for the given test case name of the test class.
     */
    void writeTestOutput(long classId, long testId, TestOutputEvent.Destination destination, Writer writer);
    
    void writeTestOutput(long classId, long testId, WriterOutputEnricher enricher, Writer writer);

    /**
     * Visits the results of each test class, in no specific order. Each class is visited exactly once.
     */
    void visitClasses(Action<? super TestClassResult> visitor);


    boolean hasOutput(long id, TestOutputEvent.Destination destination);

    /**
     * Check if we have any output available (out or err)
     */
    boolean hasOutput(long id);

    boolean isHasResults();

    /**
     * Interface for handling pre and post conditions during writing test output
     */
    interface WriterOutputEnricher {

        /**
         * testId > 0 means this is test
         * 
         * @return true if message must be filtered (skipped)
         */
        void enrichPre(long testId, TestOutputEvent.Destination destination) throws IOException;

        /**
         * see {@link #filterPre(long, org.gradle.api.tasks.testing.TestOutputEvent.Destination)}
         */
        void enrichPost(long testId, TestOutputEvent.Destination destination) throws IOException;
        
        /**
         * Will be called after complete of writing data
         */
        void complete() throws IOException;
    }
}
