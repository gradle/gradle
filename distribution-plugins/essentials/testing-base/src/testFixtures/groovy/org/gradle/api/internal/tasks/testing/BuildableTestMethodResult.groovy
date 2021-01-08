/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.api.internal.tasks.testing

import org.gradle.api.internal.tasks.testing.junit.result.TestMethodResult
import org.gradle.api.tasks.testing.TestOutputEvent
import org.gradle.api.tasks.testing.TestResult

class BuildableTestMethodResult extends TestMethodResult {
    List<Throwable> exceptions = []

    TestResult.ResultType resultType = TestResult.ResultType.SUCCESS

    private final List<MethodTestOutputEvent> outputEvents

    BuildableTestMethodResult(String name, List<MethodTestOutputEvent> outputEvents, TestResult result) {
        super(name, result)
        this.outputEvents = outputEvents
    }

    void failure(String message, String text = message) {
        failure(new TestResultException(message, text))
    }

    void failure(Exception e) {
        exceptions.add(e)
        resultType = TestResult.ResultType.FAILURE
    }

    def stderr(String output) {
        outputEvents << new MethodTestOutputEvent(name, new DefaultTestOutputEvent(TestOutputEvent.Destination.StdErr, output))
    }

    def stdout(String output) {
        outputEvents << new MethodTestOutputEvent(name, new DefaultTestOutputEvent(TestOutputEvent.Destination.StdOut, output))
    }

    static class TestResultException extends Exception {

        private final String message
        private final String text

        TestResultException(String message, String text) {
            super(message)
            this.text = text
        }

        String toString() {
            return message
        }

        public void printStackTrace(PrintWriter s) {
            s.print(text);
        }
    }

}
