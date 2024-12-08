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

import org.gradle.api.Action
import org.gradle.api.internal.tasks.testing.junit.result.PersistentTestResult
import org.gradle.api.internal.tasks.testing.junit.result.TestResultsProvider
import org.gradle.api.tasks.testing.TestOutputEvent
import org.gradle.api.tasks.testing.TestResult

import static org.gradle.api.tasks.testing.TestOutputEvent.Destination.StdErr
import static org.gradle.api.tasks.testing.TestOutputEvent.Destination.StdOut

class BuildableTestResultsProvider implements TestResultsProvider {

    private PersistentTestResult result
    private List<BuildableTestResultsProvider> children = []
    private List<TestOutputEvent> outputEvents = []

    BuildableTestResultsProvider() {
    }

    @Override
    PersistentTestResult getResult() {
        return result
    }

    void result(String name, @DelegatesTo(value = PersistentTestResult.Builder, strategy = Closure.DELEGATE_FIRST) Closure<?> action = {}) {
        resultShared(name, false, action)
    }

    void resultForClass(String name, @DelegatesTo(value = PersistentTestResult.Builder, strategy = Closure.DELEGATE_FIRST) Closure<?> action = {}) {
        resultShared(name, true, action)
    }

    private resultShared(String name, boolean isClass, Closure<?> action) {
        PersistentTestResult.Builder result = PersistentTestResult.builder()
            .name(name)
            .displayName(name)
            .startTime(0)
            .endTime(1000)
            .resultType(TestResult.ResultType.SUCCESS)
        if (isClass) {
            result.legacyProperties(new PersistentTestResult.LegacyProperties(isClass, name, name))
        }
        action.delegate = result
        action.resolveStrategy = Closure.DELEGATE_FIRST
        action()
        this.result = result.build()
    }

    void stdout(String output) {
        outputEvents << new DefaultTestOutputEvent(System.currentTimeMillis(), StdOut, output)
    }

    void stderr(String output) {
        outputEvents << new DefaultTestOutputEvent(System.currentTimeMillis(), StdErr, output)
    }

    BuildableTestResultsProvider child(@DelegatesTo(value = BuildableTestResultsProvider, strategy = Closure.DELEGATE_FIRST) Closure<?> action) {
        def child = new BuildableTestResultsProvider()
        action.delegate = child
        action.resolveStrategy = Closure.DELEGATE_FIRST
        action()
        children << child
        return child
    }

    @Override
    void copyOutput(TestOutputEvent.Destination destination, Writer writer) {
        outputEvents.each {
            if (it.destination == destination) {
                writer.write(it.message)
            }
        }
    }

    @Override
    boolean hasOutput(TestOutputEvent.Destination destination) {
        return outputEvents.any { it.destination == destination }
    }

    @Override
    boolean hasAllOutput(TestOutputEvent.Destination destination) {
        if (hasOutput(destination)) {
            return true
        }

        return children.any { it.hasAllOutput(destination) }
    }

    @Override
    boolean hasChildren() {
        return !children.isEmpty()
    }

    @Override
    void visitChildren(Action<? super TestResultsProvider> visitor) {
        children.each {
            visitor.execute(it)
        }
    }

    void close() throws IOException {
        // nothing
    }
}
