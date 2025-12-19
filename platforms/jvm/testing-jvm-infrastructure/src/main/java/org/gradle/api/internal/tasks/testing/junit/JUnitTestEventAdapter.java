/*
 * Copyright 2010 the original author or authors.
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

package org.gradle.api.internal.tasks.testing.junit;

import org.gradle.api.internal.tasks.testing.ClassTestDefinition;
import org.gradle.api.internal.tasks.testing.DefaultTestClassDescriptor;
import org.gradle.api.internal.tasks.testing.DefaultTestDescriptor;
import org.gradle.api.internal.tasks.testing.DefaultTestFailure;
import org.gradle.api.internal.tasks.testing.DefaultTestSuiteDescriptor;
import org.gradle.api.internal.tasks.testing.TestCompleteEvent;
import org.gradle.api.internal.tasks.testing.TestDescriptorInternal;
import org.gradle.api.internal.tasks.testing.TestResultProcessor;
import org.gradle.api.internal.tasks.testing.TestStartEvent;
import org.gradle.api.internal.tasks.testing.failure.DefaultThrowableToTestFailureMapper;
import org.gradle.api.internal.tasks.testing.failure.TestFailureMapper;
import org.gradle.api.internal.tasks.testing.failure.mappers.AssertErrorMapper;
import org.gradle.api.internal.tasks.testing.failure.mappers.AssertjMultipleAssertionsErrorMapper;
import org.gradle.api.internal.tasks.testing.failure.mappers.JUnitComparisonTestFailureMapper;
import org.gradle.api.internal.tasks.testing.failure.mappers.OpenTestAssertionFailedMapper;
import org.gradle.api.internal.tasks.testing.failure.mappers.OpenTestMultipleFailuresErrorMapper;
import org.gradle.api.tasks.testing.TestFailure;
import org.gradle.api.tasks.testing.TestResult;
import org.gradle.internal.id.IdGenerator;
import org.gradle.internal.time.Clock;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.junit.runner.Description;
import org.junit.runner.Result;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunListener;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * A {@link RunListener} that maps JUnit4 events to Gradle test events.
 *
 * <p>
 * This class is assumed to be synchronized by JUnit 4, so it does not do any additional synchronization itself.
 * </p>
 */
@NullMarked
public class JUnitTestEventAdapter extends RunListener {

    private static final List<TestFailureMapper> MAPPERS = Arrays.asList(
        new JUnitComparisonTestFailureMapper(),
        new OpenTestAssertionFailedMapper(),
        new OpenTestMultipleFailuresErrorMapper(),
        new AssertjMultipleAssertionsErrorMapper(),
        new AssertErrorMapper()
    );

    @NullMarked
    private static final class TestNode {
        @Nullable
        private final TestNode parent;
        private final Description description;
        private final TestDescriptorInternal descriptor;

        private TestNode(@Nullable TestNode parent, Description description, TestDescriptorInternal descriptor) {
            this.parent = parent;
            this.description = description;
            this.descriptor = descriptor;
        }

        @Override
        public String toString() {
            return "TestNode{name=" + descriptor.getName() + ",id=" + descriptor.getId() + "}";
        }
    }

    private static final DefaultThrowableToTestFailureMapper FAILURE_MAPPER = new DefaultThrowableToTestFailureMapper(MAPPERS);

    private static final Pattern DESCRIPTOR_PATTERN = Pattern.compile("(.*)\\((.*)\\)(\\[\\d+])?", Pattern.DOTALL);
    private final IdGenerator<?> idGenerator;
    private final TestResultProcessor resultProcessor;
    private final Clock clock;
    // This uses a Deque so grandparents are completed after parents
    private final Deque<TestNode> executingStack = new ArrayDeque<>();
    private final Map<Thread, Deque<TestNode>> executingStackPerThread = new HashMap<>();
    private final Set<TestNode> executing = new LinkedHashSet<>();
    private final DescriptionSet assumptionFailed = new DescriptionSet();
    private boolean testsStarted = false;
    @Nullable
    private String rootName;
    @Nullable
    private TestNode rootNode;
    private final DescriptionMap<TestNode, TestNode> descriptionsToDescriptors = new DescriptionMap<>(
        (desc, v) -> v,
        new DescriptionMap.DescriptionWitness<TestNode, TestNode>() {
            @Override
            public Description getDescription(TestNode wrappedValue) {
                return wrappedValue.description;
            }

            @Override
            public TestNode getValue(TestNode wrappedValue) {
                return wrappedValue;
            }
        }
    );
    private final Map<Description, Integer> descriptionToStartedCount = new LinkedHashMap<>();

    public JUnitTestEventAdapter(TestResultProcessor resultProcessor, Clock clock, IdGenerator<?> idGenerator) {
        this.resultProcessor = resultProcessor;
        this.clock = clock;
        this.idGenerator = idGenerator;
    }

    /**
     * Sets the root name. This is used to override the root description name, as some runners
     * don't set a name on the root description, e.g. {@link org.junit.internal.runners.JUnit38ClassRunner}.
     *
     * @param rootName the root name
     */
    public void setRootName(String rootName) {
        this.rootName = rootName;
    }

    @Nullable
    private TestNode getNodeOrNext(Description description) {
        TestNode unambiguousNode = descriptionsToDescriptors.get(description);
        if (unambiguousNode != null) {
            return unambiguousNode;
        }
        int startedCount = descriptionToStartedCount.getOrDefault(description, 0);
        List<TestNode> candidates = descriptionsToDescriptors.getByEquality(description);
        if (startedCount >= candidates.size()) {
            return null;
        }
        return candidates.get(startedCount);
    }

    private Deque<TestNode> getExecutingStackForCurrentThread() {
        return executingStackPerThread.computeIfAbsent(Thread.currentThread(), t -> new ArrayDeque<>());
    }

    @Nullable
    private TestNode getNodeOrCurrent(Description description) {
        TestNode unambiguousNode = descriptionsToDescriptors.get(description);
        if (unambiguousNode != null) {
            return unambiguousNode;
        }
        TestNode lastStarted = getExecutingStackForCurrentThread().peekLast();
        if (lastStarted != null && lastStarted.description.equals(description)) {
            return lastStarted;
        }
        return null;
    }

    @NullMarked
    private enum RegistrationMode {
        TEST,
        SUITE,
        DETECTED
    }

    private TestNode getOrRegisterNextNode(Description description, RegistrationMode registrationMode) {
        TestNode node = getNodeOrNext(description);
        if (node != null) {
            return node;
        }
        return registerNode(description, registrationMode);
    }

    private TestNode registerNode(Description description, RegistrationMode registrationMode) {
        TestNode parent = getParentForDynamicRegistration(description);
        TestNode node = createNode(parent, description, registrationMode);
        descriptionsToDescriptors.put(description, node);
        return node;
    }

    private TestNode getParentForDynamicRegistration(Description description) {
        Class<?> testClass = getTestClassIfPossible(description);
        if (testClass != null) {
            Description fakeParentDescription = Description.createSuiteDescription(testClass);
            TestNode node = getNodeOrNext(fakeParentDescription);
            if (node != null) {
                return node;
            }
        }
        return rootNode;
    }

    /**
     * Start the given parent node if it is not already active. Note that this differs from the other methods
     * as it takes the parent node directly, rather than looking it up from a child description.
     *
     * <p>
     * This method also starts any ancestor nodes that are not already active.
     * </p>
     *
     * @param node the parent node to start
     * @param now the current time
     */
    private void startNodeIfNeeded(@Nullable TestNode node, long now) {
        // nothing to start
        if (node == null) {
            return;
        }
        // already started
        if (executing.contains(node)) {
            return;
        }
        // make sure parent is started
        startNodeIfNeeded(node.parent, now);

        startNode(node, now);
    }

    private void startNode(TestNode node, long now) {
        testsStarted = true;
        executing.add(node);
        executingStack.addLast(node);
        getExecutingStackForCurrentThread().addLast(node);
        descriptionToStartedCount.compute(
            node.description, (desc, count) -> count == null ? 1 : count + 1
        );
        resultProcessor.started(node.descriptor, startEvent(node, now));
    }

    private TestStartEvent startEvent(TestNode child, long now) {
        Object parentId = null;
        if (child.parent != null) {
            parentId = child.parent.descriptor.getId();
        }
        return new TestStartEvent(now, parentId);
    }

    private void completeNode(TestNode node, TestResult.@Nullable ResultType result, long now) {
        executing.remove(node);
        // The node is more likely to be at the end because we shouldn't finish parents before children
        executingStack.removeLastOccurrence(node);
        getExecutingStackForCurrentThread().removeLastOccurrence(node);
        resultProcessor.completed(node.descriptor.getId(), completeEvent(result, now));
    }

    private TestCompleteEvent completeEvent(TestResult.@Nullable ResultType result, long now) {
        return new TestCompleteEvent(now, result);
    }

    // Note: This is JUnit 4.13+ only, so it may not be called
    // We only use it to provide more exact timing for suites
    // If not called, the suite start time is when the first test starts
    @Override
    public void testSuiteStarted(Description description) {
        TestNode node = getOrRegisterNextNode(description, RegistrationMode.SUITE);
        startNodeIfNeeded(node, clock.getCurrentTime());
    }

    // Note: This is JUnit 4.13+ only, so it may not be called
    // We only use it to provide more exact timing for suites
    // If not called, the suite end time is when the whole run finishes
    @Override
    public void testSuiteFinished(Description description) {
        TestNode node = getNodeOrCurrent(description);
        if (node == null || !executing.contains(node)) {
            return;
        }
        completeNode(node, null, clock.getCurrentTime());
    }

    @Override
    public void testStarted(Description description) {
        TestNode node = getOrRegisterNextNode(description, RegistrationMode.TEST);
        startNodeIfNeeded(node, clock.getCurrentTime());
    }

    @Override
    public void testFailure(Failure failure) {
        addFailure(failure, null, this::reportFailure);
    }

    private void reportFailure(Object descriptorId, Throwable throwable) {
        TestFailure failure = FAILURE_MAPPER.createFailure(throwable);
        resultProcessor.failure(descriptorId, failure);
    }

    @Override
    public void testAssumptionFailure(Failure failure) {
        assumptionFailed.add(failure.getDescription());

        addFailure(failure, TestResult.ResultType.SKIPPED, this::reportAssumptionFailure);
    }

    private void reportAssumptionFailure(Object descriptorId, Throwable throwable) {
        TestFailure assumptionFailure = DefaultTestFailure.fromTestAssumptionFailure(throwable);
        resultProcessor.failure(descriptorId, assumptionFailure);
    }

    private void addFailure(Failure failure, TestResult.@Nullable ResultType resultType, BiConsumer<Object, Throwable> reportFailureMethod) {
        TestNode node = getNodeOrCurrent(failure.getDescription());
        if (node != null && !node.descriptor.isComposite() && executing.contains(node)) {
            // This is the normal path, we've just seen a test failure
            // for a test that we saw start
            reportFailureMethod.accept(node.descriptor.getId(), failure.getException());
        } else {
            // This can happen when, for example, a @BeforeClass or @AfterClass method fails
            // We generate an artificial start/failure/completed sequence of events
            TestNode newNode = registerNode(failure.getDescription(), RegistrationMode.TEST);
            startNodeIfNeeded(newNode, clock.getCurrentTime());
            reportFailureMethod.accept(newNode.descriptor.getId(), failure.getException());
            completeNode(newNode, resultType, clock.getCurrentTime());
        }
    }

    /**
     * This is not a JUnit 4 callback, but is used by {@link JUnitTestExecutor} to report an exception
     * thrown from JUnit itself.
     *
     * @param testClassDefinition information about the test class being executed when the failure occurred
     * @param failure the failure
     */
    public void testExecutionFailure(ClassTestDefinition testClassDefinition, TestFailure failure) {
        try {
            List<TestNode> executingTests = executing.stream()
                .filter(t -> !t.descriptor.isComposite())
                .collect(Collectors.toList());
            // If all currently executing tests are composite (suites), then we assume that the failure happened while
            // no actual test was running.
            // We should attach it to any currently running container,
            // or create a fake test if there is no currently running container.
            if (executingTests.isEmpty()) {
                String testName = testsStarted ? "executionError" : "initializationError";
                Description failureDescription;
                TestNode likelyCulprit = executingStack.peekLast();
                if (likelyCulprit == null) {
                    // We never started any tests, so we need to create a root node
                    Description fakeClassDescription = Description.createSuiteDescription(testClassDefinition.getTestClassName());
                    failureDescription = Description.createTestDescription(
                        testClassDefinition.getTestClassName(), testName
                    );
                    fakeClassDescription.addChild(failureDescription);
                    testRunStarted(fakeClassDescription);
                } else {
                    // Create a fake test under the likely culprit
                    failureDescription = Description.createTestDescription(
                        likelyCulprit.description.getClassName(), testName
                    );
                }
                TestNode newNode = registerNode(failureDescription, RegistrationMode.TEST);
                startNode(newNode, clock.getCurrentTime());
                resultProcessor.failure(newNode.descriptor.getId(), failure);
                completeNode(newNode, null, clock.getCurrentTime());
            } else {
                // Mark all currently executing tests as failed
                executingTests.forEach(node -> resultProcessor.failure(node.descriptor.getId(), failure));
                // Completion will be handled in handleRunFinished
            }
        } finally {
            handleRunFinished();
        }
    }

    @Override
    public void testIgnored(Description description) {
        if (methodName(description) == null) {
            // An @Ignored class, ignore the event. We don't get testIgnored events for each method, so we have
            // generate them on our own
            processIgnoredClass(description);
        } else {
            TestNode node = getOrRegisterNextNode(description, RegistrationMode.DETECTED);
            startNodeIfNeeded(node, clock.getCurrentTime());
            completeNode(node, TestResult.ResultType.SKIPPED, clock.getCurrentTime());
        }
    }

    private void processIgnoredClass(Description description) {
        TestNode classNode = getOrRegisterNextNode(description, RegistrationMode.SUITE);
        startNodeIfNeeded(classNode, clock.getCurrentTime());
        String className = className(description);
        for (Description childDescription : IgnoredTestDescriptorProvider.getAllDescriptions(description, className)) {
            TestNode childNode = getOrRegisterNextNode(childDescription, RegistrationMode.DETECTED);
            startNode(childNode, clock.getCurrentTime());
            completeNode(childNode, TestResult.ResultType.SKIPPED, clock.getCurrentTime());
        }
    }

    @Override
    public void testFinished(Description description) {
        long endTime = clock.getCurrentTime();
        TestNode node = getNodeOrCurrent(description);
        if (node == null || !executing.contains(node)) {
            return;
        }
        if (!executing.remove(node)) {
            if (executing.size() != 1) {
                throw new AssertionError(String.format("Unexpected end event for %s", description));
            }
            // Assume that test has renamed itself
            node = executing.iterator().next();
            executing.clear();
        }
        TestResult.ResultType resultType = assumptionFailed.remove(description) ? TestResult.ResultType.SKIPPED : null;
        completeNode(node, resultType, endTime);
    }

    @Override
    public void testRunStarted(Description description) {
        rootNode = new TestNode(null, description, new DefaultTestClassDescriptor(idGenerator.generateId(), rootName, classDisplayName(rootName)));
        addDescriptorAndChildren(description, rootNode);

        // Start root immediately so output is captured for it
        startNodeIfNeeded(rootNode, clock.getCurrentTime());
    }

    @Override
    public void testRunFinished(Result result) {
        handleRunFinished();
    }

    private void handleRunFinished() {
        // Complete any active nodes, in reverse order
        long now = clock.getCurrentTime();
        TestNode node;
        while ((node = executingStack.pollLast()) != null) {
            resultProcessor.completed(node.descriptor.getId(), new TestCompleteEvent(now));
        }

        executing.clear();
        executingStackPerThread.clear();
        assumptionFailed.clear();
        testsStarted = false;
        rootName = null;
        rootNode = null;
        descriptionsToDescriptors.clear();
        descriptionToStartedCount.clear();
    }

    private void addDescriptorAndChildren(Description parent, TestNode parentNode) {
        descriptionsToDescriptors.put(parent, parentNode);
        for (Description child : parent.getChildren()) {
            TestNode node = createNode(parentNode, child, RegistrationMode.DETECTED);
            addDescriptorAndChildren(child, node);
        }
    }

    @Nullable
    private static final Method DESCRIPTION_GET_TEST_CLASS;

    static {
        Method getTestClass;
        try {
            getTestClass = Description.class.getDeclaredMethod("getTestClass");
        } catch (NoSuchMethodException e) {
            // Assume JUnit <= 4.5
            getTestClass = null;
        }
        DESCRIPTION_GET_TEST_CLASS = getTestClass;
    }

    private static boolean supportsTestClassMethod() {
        return DESCRIPTION_GET_TEST_CLASS != null;
    }

    @Nullable
    private static Class<?> getTestClassIfPossible(Description description) {
        if (!supportsTestClassMethod()) {
            return null;
        }
        try {
            return (Class<?>) DESCRIPTION_GET_TEST_CLASS.invoke(description);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException) {
                throw (RuntimeException) e.getCause();
            }
            throw new RuntimeException(e);
        } catch (IllegalAccessException e) {
            throw new AssertionError("Should always have access to getTestClass when present", e);
        }
    }

    // Use this instead of Description.getMethodName(), it is not available in JUnit <= 4.5
    @Nullable
    public static String methodName(Description description) {
        return methodName(description.toString());
    }

    @Nullable
    public static String methodName(String description) {
        Matcher matcher = methodStringMatcher(description);
        if (matcher.matches()) {
            return matcher.group(1);
        }
        return null;
    }

    // Use this instead of Description.getClassName(), it is not available in JUnit <= 4.5
    public static String className(Description description) {
        return className(description.toString());
    }

    public static String className(String description) {
        Matcher matcher = methodStringMatcher(description);
        return matcher.matches() ? matcher.group(2) : description;
    }

    private static String classDisplayName(String className) {
        // Use last part of class name only, for legacy compatibility
        int lastDot = className.lastIndexOf('.');
        if (lastDot > 0) {
            return className.substring(lastDot + 1);
        } else {
            return className;
        }
    }

    private static Matcher methodStringMatcher(String description) {
        return DESCRIPTOR_PATTERN.matcher(description);
    }

    private TestNode createNode(TestNode parent, Description description, RegistrationMode registrationMode) {
        Object id = idGenerator.generateId();
        String className = className(description);
        TestDescriptorInternal descriptor;
        if (registrationMode == RegistrationMode.SUITE || (registrationMode == RegistrationMode.DETECTED && description.isSuite())) {
            if (supportsTestClassMethod() && getTestClassIfPossible(description) == null) {
                descriptor = new DefaultTestSuiteDescriptor(id, className);
            } else {
                descriptor = new DefaultTestClassDescriptor(id, className, classDisplayName(className));
            }
        } else {
            String methodName = methodName(description);
            if (methodName != null) {
                descriptor = new DefaultTestDescriptor(id, className, methodName);
            } else {
                descriptor = new DefaultTestDescriptor(id, className, "classMethod");
            }
        }
        return new TestNode(parent, description, descriptor);
    }
}
