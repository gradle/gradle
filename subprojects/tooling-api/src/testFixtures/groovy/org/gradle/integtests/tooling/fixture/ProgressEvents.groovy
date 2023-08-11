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

import junit.framework.AssertionFailedError
import org.gradle.api.specs.Spec
import org.gradle.internal.os.OperatingSystem
import org.gradle.test.fixtures.resource.RemoteArtifact
import org.gradle.tooling.Failure
import org.gradle.tooling.events.FailureResult
import org.gradle.tooling.events.FinishEvent
import org.gradle.tooling.events.OperationDescriptor
import org.gradle.tooling.events.OperationResult
import org.gradle.tooling.events.ProgressEvent
import org.gradle.tooling.events.ProgressListener
import org.gradle.tooling.events.StartEvent
import org.gradle.tooling.events.SuccessResult
import org.gradle.tooling.events.configuration.ProjectConfigurationFinishEvent
import org.gradle.tooling.events.configuration.ProjectConfigurationOperationDescriptor
import org.gradle.tooling.events.configuration.ProjectConfigurationStartEvent
import org.gradle.tooling.events.download.FileDownloadFinishEvent
import org.gradle.tooling.events.download.FileDownloadOperationDescriptor
import org.gradle.tooling.events.download.FileDownloadResult
import org.gradle.tooling.events.download.FileDownloadStartEvent
import org.gradle.tooling.events.problems.ProblemDescriptor
import org.gradle.tooling.events.task.TaskFinishEvent
import org.gradle.tooling.events.task.TaskOperationDescriptor
import org.gradle.tooling.events.task.TaskStartEvent
import org.gradle.tooling.events.test.TestFinishEvent
import org.gradle.tooling.events.test.TestOperationDescriptor
import org.gradle.tooling.events.test.TestStartEvent
import org.gradle.tooling.events.transform.TransformFinishEvent
import org.gradle.tooling.events.transform.TransformOperationDescriptor
import org.gradle.tooling.events.transform.TransformStartEvent
import org.gradle.tooling.events.work.WorkItemFinishEvent
import org.gradle.tooling.events.work.WorkItemOperationDescriptor
import org.gradle.tooling.events.work.WorkItemStartEvent
import org.gradle.util.GradleVersion

import java.util.function.Predicate

class ProgressEvents implements ProgressListener {
    private final List<ProgressEvent> events = []
    private boolean dirty
    private final List<Operation> operations = new ArrayList<Operation>()
    private static final boolean IS_WINDOWS_OS = OperatingSystem.current().isWindows()

    /**
     * Creates a {@link ProgressEvents} implementation for the current tooling api client version.
     */
    static ProgressEvents create() {
        return GradleVersion.current().baseVersion < GradleVersion.version("3.5") ? new ProgressEvents() : new ProgressEventsWithStatus()
    }

    protected ProgressEvents() {
    }

    void clear() {
        events.clear()
        operations.clear()
        dirty = false
    }

    /**
     * Asserts that the events form zero or more well-formed trees of operations.
     */
    void assertHasZeroOrMoreTrees() {
        if (!dirty) {
            return;
        }

        Set<OperationDescriptor> seen = []
        Map<OperationDescriptor, StartEvent> running = [:]
        for (ProgressEvent event : events) {
            assert event.displayName == event.toString()
            assert event.descriptor.displayName
            assert event.descriptor.displayName == event.descriptor.toString()
            assert event.descriptor.name

            if (event instanceof StartEvent) {
                def descriptor = event.descriptor
                assert seen.add(descriptor)
                assert !running.containsKey(descriptor)
                running[descriptor] = event

                // Display name should be mostly unique
                if (uniqueBuildOperation(descriptor)) {
                    if (descriptor.displayName.contains('/maven-metadata.xml')
                        || descriptor.displayName.startsWith('Apply plugin ')
                        || descriptor.displayName.startsWith('Configure project ')
                        || descriptor.displayName.startsWith('Cross-configure project ')
                        || descriptor.displayName.startsWith('Resolve files of')
                        || descriptor.displayName.startsWith('Identifying ')
                        || descriptor.displayName.startsWith('Execute unit of work')
                        || descriptor.displayName.startsWith('Executing ')
                        || descriptor.displayName.startsWith('Execute container callback action')
                        || descriptor.displayName.startsWith('Resolving ')
                    ) {
                        // Ignore this for now
                    } else {
                        def duplicateName = operations.find({
                            !it.failed && // ignore previous operations with the same display name that failed, eg for retry of downloads
                                it.descriptor.displayName == descriptor.displayName &&
                                it.parent?.descriptor == descriptor.parent
                        })
                        if (duplicateName != null) {
                            // Same display name and same parent
                            throw new AssertionFailedError("Found duplicate operation '${duplicateName}' in events:\n${describeList(events)}")
                        }
                    }
                }

                // parent should also be running
                assert descriptor.parent == null || running.containsKey(descriptor.parent)
                def parent = descriptor.parent == null ? null : operations.find { it.descriptor == descriptor.parent }

                Operation operation = newOperation(event, parent, descriptor)
                operations.add(operation)

                assert descriptor.displayName == descriptor.toString()
                assert event.displayName == "${descriptor.displayName} started" as String
            } else if (event instanceof FinishEvent) {
                def descriptor = event.descriptor
                def startEvent = running.remove(descriptor)
                assert startEvent != null

                // parent should still be running
                assert descriptor.parent == null || running.containsKey(descriptor.parent)

                def storedOperation = operations.find { it.descriptor == descriptor }
                storedOperation.finishEvent = event
                storedOperation.result = event.result

                assert event.displayName.matches("\\Q${descriptor.displayName}\\E[ \\w-]+")

                // don't check event timestamp order on Windows OS
                // timekeeping in CI environment on Windows is currently problematic
                if (!IS_WINDOWS_OS) {
                    assert startEvent.eventTime <= event.eventTime
                }

                assert event.result.startTime == startEvent.eventTime
                assert event.result.endTime == event.eventTime
            } else {
                def descriptor = event.descriptor
                // operation should still be running
                if (descriptor instanceof ProblemDescriptor) {
                    continue
                }
                assert running.containsKey(descriptor)
                def operation = operations.find { it.descriptor == event.descriptor }
                otherEvent(event, operation)
            }
        }
        assert running.size() == 0: "Not all operations completed: ${running.values()}, events: ${events}"

        dirty = false
    }

    protected Operation newOperation(StartEvent startEvent, Operation parent, OperationDescriptor descriptor) {
        new Operation(startEvent, parent, descriptor)
    }

    protected void otherEvent(ProgressEvent event, Operation operation) {
        throw new AssertionError("Unexpected type of progress event received: ${event.getClass()}")
    }

    // Ignore this check for TestOperationDescriptors as they are currently not unique when coming from different test tasks
    // Ignore resolve artifact operations as they are not necessarily unique atm
    boolean uniqueBuildOperation(OperationDescriptor operationDescriptor) {
        return !(operationDescriptor instanceof TestOperationDescriptor) && !operationDescriptor.displayName.startsWith("Resolve artifact ")
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
     * Returns all operations with no parent, in the order started.
     */
    List<Operation> getTrees() {
        assertHasZeroOrMoreTrees()
        return operations.findAll { it.descriptor.parent == null }
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
        def testOperations = operations.findAll { it.test } as List
        testOperations.forEach { it.assertIsTest() }
        return testOperations
    }

    /**
     * Returns all events for test class and method execution
     */
    List<Operation> getTestClassesAndMethods() {
        assertHasZeroOrMoreTrees()
        return operations.findAll { it.testClassOrMethod } as List
    }


    /**
     * Returns all events for test task or executor execution
     */
    List<Operation> getTestTasksAndExecutors() {
        assertHasZeroOrMoreTrees()
        return operations.findAll { it.test && !it.testClassOrMethod } as List
    }

    /**
     * Returns all tasks, in the order started.
     */
    List<Operation> getTasks() {
        assertHasZeroOrMoreTrees()
        def taskOperations = operations.findAll { it.task } as List
        taskOperations.forEach { it.assertIsTask() }
        return taskOperations
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
     * Returns the operation with the given display name. Fails when there is not exactly one such operation.
     *
     * @param displayNames candidate display names (may be different depending on the Gradle version under test)
     */
    Operation operation(String... displayNames) {
        def candidates = operations(displayNames)
        if (candidates.empty) {
            throw new AssertionFailedError("No operation with display name '${displayNames[0]}' found in:\n${describeList(operations)}")
        }
        if (candidates.size() != 1) {
            throw new AssertionFailedError("Multiple operation with display name '${displayNames[0]}' found in:\n${describeList(operations)}")
        }
        return candidates[0]
    }

    /**
     * Returns the operations with the given display name.
     *
     * @param displayNames candidate display names (may be different depending on the Gradle version under test)
     */
    List<Operation> operations(String... displayNames) {
        assertHasZeroOrMoreTrees()
        return operations.findAll { it.descriptor.displayName in displayNames }
    }

    /**
     * Returns the first operation with a display name matching the given regex. Fails when an operation is not found.
     *
     * @param regex candidate display names (may be different depending on the Gradle version under test)
     */
    Operation operationMatches(String regex) {
        assertHasZeroOrMoreTrees()
        def operation = operations.find { it.descriptor.displayName.matches(regex) }
        if (operation == null) {
            throw new AssertionFailedError("No operation matching regex '${regex}' found in:\n${describeList(operations)}")
        }
        return operation
    }

    /**
     * Returns the operation with the given display name. Fails when there is not exactly one such operation.
     *
     * @param displayNames candidate display names (may be different depending on the Gradle version under test)
     */
    Operation operation(Operation parent, String... displayNames) {
        assertHasZeroOrMoreTrees()
        def operation = operations.find { it.parent == parent && it.descriptor.displayName in displayNames }
        if (operation == null) {
            throw new AssertionFailedError("No operation with display name '${displayNames[0]}' and parent '$parent' found in:\n${describeList(operations)}")
        }
        return operation
    }

    @Override
    void statusChanged(ProgressEvent event) {
        dirty = true
        operations.clear()
        events << event
    }

    static class Operation {
        final StartEvent startEvent
        final OperationDescriptor descriptor
        final Operation parent
        final List<Operation> children = []
        final List<OperationStatus> statusEvents = []
        FinishEvent finishEvent
        OperationResult result

        protected Operation(StartEvent startEvent, Operation parent, OperationDescriptor descriptor) {
            this.startEvent = startEvent
            this.descriptor = descriptor
            this.parent = parent
            if (parent != null) {
                parent.children.add(this)
            }
        }

        @Override
        String toString() {
            return descriptor.displayName
        }

        boolean isTest() {
            return descriptor instanceof TestOperationDescriptor
        }

        boolean isTestClassOrMethod() {
            return isTest() && (descriptor.className || descriptor.methodName)
        }

        boolean isTask() {
            return descriptor instanceof TaskOperationDescriptor
        }

        boolean isWorkItem() {
            try {
                // the class is not present in pre 5.1 TAPI
                return descriptor instanceof WorkItemOperationDescriptor
            } catch (NoClassDefFoundError ignore) {
                false
            }
        }

        boolean isProjectConfiguration() {
            try {
                // the class is not present in pre 5.1 TAPI
                return descriptor instanceof ProjectConfigurationOperationDescriptor
            } catch (NoClassDefFoundError ignore) {
                false
            }
        }

        boolean isTransform() {
            try {
                // the class is not present in pre 5.1 TAPI
                return descriptor instanceof TransformOperationDescriptor
            } catch (NoClassDefFoundError ignore) {
                false
            }
        }

        boolean isDownload() {
            return descriptor instanceof FileDownloadOperationDescriptor
        }

        boolean isBuildOperation() {
            return !test && !task && !workItem && !projectConfiguration && !transform
        }

        void assertIsTask() {
            assert startEvent instanceof TaskStartEvent
            assert finishEvent instanceof TaskFinishEvent
            assert descriptor instanceof TaskOperationDescriptor
        }

        void assertIsTest() {
            assert startEvent instanceof TestStartEvent
            assert finishEvent instanceof TestFinishEvent
            assert descriptor instanceof TestOperationDescriptor
        }

        void assertIsProjectConfiguration() {
            assert startEvent instanceof ProjectConfigurationStartEvent
            assert finishEvent instanceof ProjectConfigurationFinishEvent
            assert descriptor instanceof ProjectConfigurationOperationDescriptor
        }

        void assertIsWorkItem() {
            assert startEvent instanceof WorkItemStartEvent
            assert finishEvent instanceof WorkItemFinishEvent
            assert descriptor instanceof WorkItemOperationDescriptor
        }

        void assertIsTransform() {
            assert startEvent instanceof TransformStartEvent
            assert finishEvent instanceof TransformFinishEvent
            assert descriptor instanceof TransformOperationDescriptor
        }

        void assertIsDownload(RemoteArtifact artifact) {
            assertIsDownload(artifact.uri, artifact.file.length())
        }

        void assertIsDownload(URI uri, long size) {
            assert startEvent instanceof FileDownloadStartEvent
            assert finishEvent instanceof FileDownloadFinishEvent
            assert descriptor instanceof FileDownloadOperationDescriptor
            assert descriptor.uri == uri
            assert descriptor.displayName == "Download " + uri
            assert finishEvent.result instanceof FileDownloadResult
            assert finishEvent.result.bytesDownloaded == size
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

        Operation child(Spec<String> displayNameSpec) {
            def child = children.find { displayNameSpec.isSatisfiedBy(it.descriptor.displayName) }
            if (child == null) {
                throw new AssertionFailedError("No operation matching display name found in children of '$descriptor.displayName':\n${describeList(children)}")
            }
            return child
        }

        Operation child(String... displayNames) {
            def child = children.find { it.descriptor.displayName in displayNames }
            if (child == null) {
                throw new AssertionFailedError("No operation with display name '${displayNames[0]}' found in children of '$descriptor.displayName':\n${describeList(children)}")
            }
            return child
        }

        /**
         * Select child operations that have the given display name.
         *
         * @param displayName Operation display name
         * @return the selected Operations, potentially empty
         */
        List<Operation> children(String displayName) {
            return children.findAll { it.descriptor.displayName == displayName }
        }

        List<Operation> descendants(Spec<? super Operation> filter) {
            def found = [] as List<Operation>
            def recurse
            recurse = { List<Operation> children ->
                children.each { child ->
                    if (filter.isSatisfiedBy(child)) {
                        found << child
                    }
                    recurse child.children
                }
            }
            recurse children
            found
        }

        Operation descendant(String... displayNames) {
            def found = descendants { it.descriptor.displayName in displayNames }
            if (found.size() == 1) {
                return found[0]
            }
            if (found.empty) {
                throw new AssertionFailedError("No operation with display name '${displayNames[0]}' found in descendants of '$descriptor.displayName':\n${describeOperationsTree(children)}")
            }
            throw new AssertionFailedError("More than one operation with display name '${displayNames[0]}' found in descendants of '$descriptor.displayName':\n${describeOperationsTree(children)}")
        }

        boolean hasAncestor(Operation ancestor) {
            return hasAncestor({ it == ancestor })
        }

        boolean hasAncestor(Predicate<? super Operation> predicate) {
            return parent == null
                ? false
                : (predicate.test(parent) || parent.hasAncestor(predicate))
        }
    }

    static class OperationStatus {
        final ProgressEvent event

        OperationStatus(ProgressEvent event) {
            this.event = event
        }
    }

    private static String describeList(List/*<ProgressEvent OR Operation>*/ haveDescriptor) {
        return '\t' + haveDescriptor.collect { it.descriptor.displayName }.join('\n\t')
    }

    String describeOperationsTree() {
        return describeOperationsTree(operations.findAll { !it.parent })
    }

    static String describeOperationsTree(List<Operation> operations) {
        def description = ''
        def recurse
        recurse = { List<Operation> children, int level = 0 ->
            children.each { child ->
                description += "\t${' ' * level}${child.descriptor.displayName}\n"
                recurse child.children, level + 1
            }
        }
        recurse operations
        return description
    }
}
