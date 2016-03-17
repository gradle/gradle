/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.integtests.tooling.fixture

import org.gradle.internal.os.OperatingSystem
import org.gradle.tooling.Failure
import org.gradle.tooling.events.*
import org.gradle.tooling.events.task.TaskOperationDescriptor
import org.gradle.tooling.events.test.TestOperationDescriptor

class ProgressEvents implements ProgressListener {
    private final List<ProgressEvent> events = []
    private boolean dirty
    private final List<Operation> operations= new ArrayList<Operation>()
    private static final boolean IS_WINDOWS_OS = OperatingSystem.current().isWindows()
    boolean skipValidation

    void clear() {
        events.clear()
        operations.clear()
        dirty = false
    }

    /**
     * Asserts that the events form zero or more well-formed trees of operations.
     */
    void assertHasZeroOrMoreTrees() {
        if (dirty) {
            Set<OperationDescriptor> seen = []
            Map<OperationDescriptor, StartEvent> running = [:]
            for (ProgressEvent event : events) {
                assert event.displayName == event.toString()

                if (event instanceof StartEvent) {
                    def descriptor = event.descriptor
                    assert seen.add(descriptor)
                    assert !running.containsKey(descriptor)
                    running[descriptor] = event

                    // Display name should be mostly unique
                    // Ignore this check for TestOperationDescriptors as they are currently not unique when coming from different test tasks
                    if (!skipValidation && !(descriptor instanceof TestOperationDescriptor)) {
                        def duplicateName = operations.find({ it.descriptor.displayName == descriptor.displayName })
                        if (duplicateName != null) {
                            println "Found duplicate operation in events: " + events
                        }
                        assert duplicateName == null
                    }

                    Operation operation = new Operation(descriptor)
                    operations.add(operation)

                    // parent should also be running
                    assert descriptor.parent == null || running.containsKey(descriptor.parent)

                    assert descriptor.displayName == descriptor.toString()
                    assert event.displayName == "${descriptor.displayName} started" as String
                } else if (event instanceof FinishEvent) {
                    def descriptor = event.descriptor
                    def startEvent = running.remove(descriptor)
                    assert startEvent != null

                    // parent should still be running
                    assert descriptor.parent == null || running.containsKey(descriptor.parent)

                    def storedOperation = operations.find { it.descriptor == descriptor }
                    storedOperation.result = event.result

                    assert event.displayName.matches("\\Q${descriptor.displayName}\\E \\w+")

                    // don't check event timestamp order on Windows OS
                    // timekeeping in CI environment on Windows is currently problematic
                    if(!IS_WINDOWS_OS) {
                        assert startEvent.eventTime <= event.eventTime
                    }

                    assert event.result.startTime == startEvent.eventTime
                    assert event.result.endTime == event.eventTime
                } else {
                    throw new AssertionError("Unexpected type of progress event received: ${event.getClass()}")
                }
            }
            assert running.size() == 0: "Not all operations completed: ${running.values()}, events: ${events}"

            dirty = false
        }
    }

    /**
     * Asserts that the events form exactly one well-formed trees of operations.
     */
    void assertHasSingleTree() {
        assertHasZeroOrMoreTrees()
        assert !operations.empty
        assert operations[0].descriptor.parent == null

        operations.tail().each { assert it.descriptor.parent != null }
    }

    /**
     * Asserts that the events form a typical tree of operations for a build.
     */
    void assertIsABuild() {
        assertHasSingleTree()

        def root = operations[0]
        assert root.buildOperation
        assert root.descriptor.displayName == 'Run build'
    }

    boolean isEmpty() {
        assertHasZeroOrMoreTrees()
        return events.empty
    }

    /**
     * Returns all events, in the order received.
     */
    List<ProgressEvent> getAll() {
        assertHasZeroOrMoreTrees()
        return events
    }

    /**
     * Returns all operations, in the order started.
     */
    List<Operation> getOperations() {
        assertHasZeroOrMoreTrees()
        return operations
    }

    /**
     * Returns all generic build operations, in the order started.
     */
    List<Operation> getBuildOperations() {
        assertHasZeroOrMoreTrees()
        return operations.findAll { it.buildOperation } as List
    }

    /**
     * Returns all tests, in the order started.
     */
    List<Operation> getTests() {
        assertHasZeroOrMoreTrees()
        return operations.findAll { it.test } as List
    }

    /**
     * Returns all tasks, in the order started.
     */
    List<Operation> getTasks() {
        assertHasZeroOrMoreTrees()
        return operations.findAll { it.task } as List
    }

    /**
     * Returns all successful operations, in the order started.
     */
    List<Operation> getSuccessful() {
        assertHasZeroOrMoreTrees()
        return operations.findAll { it.successful } as List
    }

    /**
     * Returns all failed operations, in the order started.
     */
    List<Operation> getFailed() {
        assertHasZeroOrMoreTrees()
        return operations.findAll { it.failed } as List
    }

    /**
     * Returns the operation with the given display name. Fails whe not exactly one such operation.
     */
    Operation operation(String displayName) {
        assertHasZeroOrMoreTrees()
        def byName = operations.find({it.descriptor.displayName == displayName})
        assert byName != null
        return byName
    }

    @Override
    void statusChanged(ProgressEvent event) {
        dirty = true
        operations.clear()
        events << event
    }

    static class Operation {
        final OperationDescriptor descriptor
        OperationResult result

        private Operation(OperationDescriptor descriptor) {
            this.descriptor = descriptor
        }

        @Override
        String toString() {
            return descriptor.displayName
        }

        boolean isTest() {
            return descriptor instanceof TestOperationDescriptor
        }

        boolean isTask() {
            return descriptor instanceof TaskOperationDescriptor
        }

        boolean isBuildOperation() {
            return !test && !task
        }

        boolean isSuccessful() {
            return result instanceof SuccessResult
        }

        boolean isFailed() {
            return result instanceof FailureResult
        }

        List<Failure> getFailures() {
            assert result instanceof FailureResult
            return result.failures
        }
    }
}
