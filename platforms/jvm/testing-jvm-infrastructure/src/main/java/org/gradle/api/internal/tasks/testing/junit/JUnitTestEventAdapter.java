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
import org.gradle.api.internal.tasks.testing.junit.description.DescriptionMap;
import org.gradle.api.internal.tasks.testing.junit.description.DescriptionSet;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.tasks.testing.TestFailure;
import org.gradle.api.tasks.testing.TestResult;
import org.gradle.internal.id.IdGenerator;
import org.gradle.internal.time.Clock;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.junit.internal.runners.JUnit38ClassRunner;
import org.junit.runner.Description;
import org.junit.runner.Result;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunListener;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A {@link RunListener} that maps JUnit4 events to Gradle test events.
 */
@NullMarked
public class JUnitTestEventAdapter extends RunListener {

    private static final Logger LOGGER = Logging.getLogger(JUnitTestEventAdapter.class);
    private static final List<TestFailureMapper> MAPPERS = Arrays.asList(
        new JUnitComparisonTestFailureMapper(),
        new OpenTestAssertionFailedMapper(),
        new OpenTestMultipleFailuresErrorMapper(),
        new AssertjMultipleAssertionsErrorMapper(),
        new AssertErrorMapper()
    );

    private static final class TestNode {
        private static final DescriptionMap.DescriptionWitness<TestNode, TestNode> WITNESS =
            new DescriptionMap.DescriptionWitness<TestNode, TestNode>() {
                @Override
                public Description getDescription(TestNode wrappedValue) {
                    return wrappedValue.description;
                }

                @Override
                public TestNode getValue(TestNode wrappedValue) {
                    return wrappedValue;
                }
            };

        public static DescriptionMap<TestNode, TestNode> createDescriptionMap() {
            return new DescriptionMap<>((desc, node) -> node, WITNESS);
        }

        /**
         * ID state field. May either be an IdGenerator or the resolved id.
         */
        private volatile Object idState;
        final Description description;

        /**
         * Constructor that avoids generating the id too early, as it leads to a confusing ordering of ids.
         * Ordering generally shouldn't be relied on, but the tests read cleaner if ids are generated in a depth-first order
         * @param idGenerator the id generator, will be used to generate the id when first needed
         * @param description the description
         */
        TestNode(IdGenerator<?> idGenerator, Description description) {
            this.idState = idGenerator;
            this.description = description;
        }

        public Object resolveId() {
            Object localIdState = idState;
            if (localIdState instanceof IdGenerator) {
                synchronized (this) {
                    localIdState = idState;
                    if (localIdState instanceof IdGenerator) {
                        IdGenerator<?> idGenerator = (IdGenerator<?>) localIdState;
                        localIdState = idGenerator.generateId();
                        // Highly unlikely, but preserve semantics if a bad IdGenerator exists
                        if (localIdState instanceof IdGenerator) {
                            throw new IllegalStateException("Generated id cannot be an IdGenerator");
                        }
                        idState = localIdState;
                    }
                }
            }
            return localIdState;
        }

        public Object requireId() {
            Object localIdState = idState;
            if (localIdState instanceof IdGenerator) {
                throw new AssertionError("ID not yet resolved for " + description);
            }
            return localIdState;
        }
    }

    private static final class PostRunStartData {
        final DescriptionMap<TestNode, TestNode> descToNode;
        final DescriptionMap<TestNode, TestNode> childDescToParentNode;

        PostRunStartData(
            DescriptionMap<TestNode, TestNode> descToNode,
            DescriptionMap<TestNode, TestNode> childDescToParentNode
        ) {
            this.descToNode = descToNode;
            this.childDescToParentNode = childDescToParentNode;
        }
    }

    private static final DefaultThrowableToTestFailureMapper FAILURE_MAPPER = new DefaultThrowableToTestFailureMapper(MAPPERS);

    private static final Pattern DESCRIPTOR_PATTERN = Pattern.compile("(.*)\\((.*)\\)(\\[\\d+])?", Pattern.DOTALL);
    private final IdGenerator<?> idGenerator;
    private final TestResultProcessor resultProcessor;
    private final Clock clock;
    private final Object lock = new Object();
    @Nullable
    private volatile PostRunStartData postRunStartData;
    // This uses a Deque so grandparents are completed after parents
    private final Deque<TestNode> activeParents = new ConcurrentLinkedDeque<>();
    private final Map<Description, Integer> descToStartedCount = new HashMap<>();
    private final Map<TestNode, DescriptionSet> parentToExecutingAmbiguousChild = new HashMap<>();
    /**
     * Set of descriptions that are currently executing as ambiguous children of a parent node.
     */
    private final DescriptionSet executingAmbiguousChildren = new DescriptionSet();
    /**
     * Parent nodes that JUnit has indicated are finished, but we are waiting for ambiguous children to complete before
     * completing the parent downstream.
     */
    private final Set<TestNode> parentsWaitingForAmbiguousChildren = new HashSet<>();
    private final DescriptionMap<TestNode, TestNode> executingToChosenParent = TestNode.createDescriptionMap();
    private final DescriptionMap<TestDescriptorInternal, DescriptionMap.SimpleValueWrapper<TestDescriptorInternal>> executing =
        DescriptionMap.createSimple();
    private final DescriptionSet assumptionFailed = new DescriptionSet();
    private volatile boolean testsStarted = false;
    @Nullable
    private volatile String rootName;

    public JUnitTestEventAdapter(TestResultProcessor resultProcessor, Clock clock, IdGenerator<?> idGenerator) {
        this.resultProcessor = resultProcessor;
        this.clock = clock;
        this.idGenerator = idGenerator;
    }

    /**
     * Sets the root name. This is used to override the root description name, as some runners
     * don't set a name on the root description, e.g. {@link JUnit38ClassRunner}.
     *
     * @param rootName the root name
     */
    public void setRootName(String rootName) {
        this.rootName = rootName;
    }

    private PostRunStartData requirePostRunStartData() {
        PostRunStartData data = this.postRunStartData;
        if (data == null) {
            throw new AssertionError("testRunStarted was not called before test events");
        }
        return data;
    }

    @Nullable
    private TestNode getNodeUnambiguously(Description description) {
        return requirePostRunStartData().descToNode.get(description);
    }

    @Nullable
    private TestNode getParentNodeUnambiguously(Description description) {
        return requirePostRunStartData().childDescToParentNode.get(description);
    }

    @Nullable
    private TestNode getParentOfForStarting(Description description) {
        TestNode chosenParent = getParentNodeUnambiguously(description);
        if (chosenParent == null) {
            // Use parent of unstarted node that matches the description
            int startedCount = descToStartedCount.getOrDefault(description, 0);
            List<TestNode> allParents = requirePostRunStartData().childDescToParentNode.getByEquality(description);
            if (allParents.isEmpty() || startedCount >= allParents.size()) {
                // All children have already started, no parent available
                return null;
            }
            chosenParent = allParents.get(startedCount);
            // Track the child so we can delay completing the parent until it is done
            addAmbiguousChild(description, chosenParent);
        }
        return chosenParent;
    }

    private void addAmbiguousChild(Description childDesc, TestNode parentNode) {
        parentToExecutingAmbiguousChild.computeIfAbsent(parentNode, k -> new DescriptionSet()).add(childDesc);
        executingAmbiguousChildren.add(childDesc);
    }

    /**
     * Start the parent of the given description if it is not already active, and throw an exception if there is no parent.
     *
     * <p>
     * This method also starts any ancestor nodes that are not already active.
     * </p>
     *
     * @param description the description whose parent should be started
     * @return the parent
     */
    private TestNode startRequiredParentIfNeeded(Description description) {
        TestNode parent = startParentIfNeeded(description);
        if (parent == null) {
            throw new AssertionError("No parent found for " + description);
        }
        return parent;
    }

    /**
     * Start the parent of the given description, if it has one, and it is not already active.
     *
     * <p>
     * This method also starts any ancestor nodes that are not already active.
     * </p>
     *
     * @param description the description whose parent should be started
     * @return the parent, or {@code null}
     */
    @Nullable
    private TestNode startParentIfNeeded(Description description) {
        TestNode chosen = executingToChosenParent.get(description);
        if (chosen != null) {
            if (!activeParents.contains(chosen)) {
                throw new AssertionError("Chosen parent " + chosen.description + " for " + description + " is not active");
            }
            return chosen;
        }
        TestNode parent = getParentOfForStarting(description);
        if (parent == null) {
            return null;
        }
        executingToChosenParent.put(description, parent);
        startParentByNodeIfNeeded(parent, clock.getCurrentTime());
        return parent;
    }

    /**
     * Start the given parent node if it is not already active. Note that this differs from the other methods
     * as it takes the parent node directly, rather than looking it up from a child description.
     *
     * <p>
     * This method also starts any ancestor nodes that are not already active.
     * </p>
     *
     * @param parent the parent node to start
     * @param now the current time
     */
    private void startParentByNodeIfNeeded(TestNode parent, long now) {
        TestNode grandparent = startParentIfNeeded(parent.description);
        if (activeParents.contains(parent)) {
            return;
        }
        activeParents.addLast(parent);
        String rootName = grandparent == null ? this.rootName : className(parent.description);
        if (rootName == null) {
            throw new AssertionError("No class name found for " + parent.description);
        }
        TestDescriptorInternal parentDescriptor;
        if (grandparent != null && supportsTestClassMethod() && getTestClassIfPossible(parent.description) == null) {
            // When Description.getTestClass() returns null and we have a grandparent,
            // it indicates "suites" of parameterized tests, or some other kind of synthetic grouping.
            // Avoid treating these as classes.
            parentDescriptor = new DefaultTestSuiteDescriptor(parent.resolveId(), rootName);
        } else {
            parentDescriptor = new DefaultTestClassDescriptor(parent.resolveId(), rootName, classDisplayName(rootName));
        }
        Object grandparentId = grandparent != null ? grandparent.requireId() : null;
        startNode(parent.description, parentDescriptor, new TestStartEvent(now, grandparentId));
    }

    private void startNode(Description description, TestDescriptorInternal descriptorInternal, TestStartEvent event) {
        descToStartedCount.compute(description, (desc, count) -> (count == null) ? 1 : count + 1);
        resultProcessor.started(descriptorInternal, event);
    }

    private void completeNode(Description description, Object id, TestCompleteEvent event) {
        resultProcessor.completed(id, event);

        if (!executingAmbiguousChildren.remove(description)) {
            return;
        }

        // Complete any parents waiting for this child
        TestNode parent = executingToChosenParent.get(description);
        if (parent == null) {
            throw new AssertionError("No parent found for executing ambiguous child " + description);
        }
        DescriptionSet ambiguousChildren = parentToExecutingAmbiguousChild.get(parent);
        if (ambiguousChildren == null) {
            throw new AssertionError("No ambiguous children executing for parent of " + description);
        }
        if (!ambiguousChildren.remove(description)) {
            throw new AssertionError("Ambiguous child " + description + " not found for parent " + parent.description);
        }
        if (ambiguousChildren.isEmpty()) {
            // No more ambiguous children executing for this parent
            parentToExecutingAmbiguousChild.remove(parent);
            // Complete the parent if it was waiting
            if (parentsWaitingForAmbiguousChildren.remove(parent)) {
                completeParentIfActive(parent);
            }
        }
    }

    // Note: This is JUnit 4.13+ only, so it may not be called
    // We only use it to provide more exact timing for suites
    // If not called, the suite start time is when the first test starts
    @Override
    public void testSuiteStarted(Description description) {
        synchronized (lock) {
            testsStarted = true;
            TestNode testNode = getNodeUnambiguously(description);
            if (testNode != null) {
                startParentByNodeIfNeeded(testNode, clock.getCurrentTime());
            }
        }
    }

    // Note: This is JUnit 4.13+ only, so it may not be called
    // We only use it to provide more exact timing for suites
    // If not called, the suite end time is when the whole run finishes
    @Override
    public void testSuiteFinished(Description description) {
        TestNode testNode = getNodeUnambiguously(description);
        if (testNode == null) {
            return;
        }
        synchronized (lock) {
            if (parentToExecutingAmbiguousChild.get(testNode) != null) {
                Logging.getLogger(getClass()).lifecycle("Delaying completion of parent {} (id {}) as it has ambiguous children still executing", testNode.description, testNode.requireId());
                // There are still ambiguous children executing, don't complete the parent yet
                parentsWaitingForAmbiguousChildren.add(testNode);
                // Make the parent of this node also wait for this node
                TestNode parentOfTestNode = executingToChosenParent.get(testNode.description);
                if (parentOfTestNode != null) {
                    addAmbiguousChild(testNode.description, parentOfTestNode);
                }
                return;
            }
            completeParentIfActive(testNode);
        }
    }

    private void completeParentIfActive(TestNode testNode) {
        if (activeParents.remove(testNode)) {
            completeNode(testNode.description, testNode.requireId(), new TestCompleteEvent(clock.getCurrentTime()));
        }
    }

    @Override
    public void testStarted(Description description) {
        synchronized (lock) {
            testsStarted = true;
            TestNode parent = startRequiredParentIfNeeded(description);
            TestDescriptorInternal descriptor = nullSafeDescriptor(idGenerator.generateId(), description);
            executing.put(description, descriptor);
            startNode(description, descriptor, startEvent(parent.requireId()));
        }
    }

    @Override
    public void testFailure(Failure failure) {
        TestDescriptorInternal testInternal;
        synchronized (lock) {
            testInternal = executing.get(failure.getDescription());

            if (testInternal != null) {
                // This is the normal path, we've just seen a test failure
                // for a test that we saw start
                Throwable exception = failure.getException();
                reportFailure(testInternal.getId(), exception);
            } else {
                // This can happen when, for example, a @BeforeClass or @AfterClass method fails
                // We generate an artificial start/failure/completed sequence of events
                withPotentiallyMissingParent(className(failure.getDescription()), clock.getCurrentTime(), parentId -> {
                    TestDescriptorInternal child = nullSafeDescriptor(idGenerator.generateId(), failure.getDescription());
                    resultProcessor.started(child, startEvent(parentId));
                    Throwable exception = failure.getException();
                    reportFailure(child.getId(), exception);
                    resultProcessor.completed(child.getId(), new TestCompleteEvent(clock.getCurrentTime()));
                });
            }
        }
    }

    /**
     * Helper method that ensures a parent with the given class name is started, even if otherwise missing.
     * The action must complete the child test immediately, as this method will complete any synthetic parent it creates
     * immediately after the action returns, and does not report the child as an ambiguous child.
     *
     * @param parentClassName the parent class name
     * @param now the current time
     * @param action the action to perform with the parent id
     */
    private void withPotentiallyMissingParent(String parentClassName, long now, Consumer<Object> action) {
        TestNode parent = startParentMatchingClassName(parentClassName, now);
        Object syntheticParentId = null;

        if (parent == null) {
            // This can happen if there's a setup method in a suite that doesn't actually get executed according to the test run data.
            // We must synthesize a parent in this case.
            syntheticParentId = idGenerator.generateId();
            DefaultTestClassDescriptor syntheticParent = new DefaultTestClassDescriptor(syntheticParentId, parentClassName);
            resultProcessor.started(syntheticParent, new TestStartEvent(now));
        }

        action.accept(parent != null ? parent.requireId() : syntheticParentId);

        if (syntheticParentId != null) {
            resultProcessor.completed(syntheticParentId, new TestCompleteEvent(now));
        }
    }

    @Nullable
    private TestNode startParentMatchingClassName(String className, long now) {
        TestNode parent = requirePostRunStartData().descToNode.getFirstMatching(desc -> className.equals(className(desc)));
        if (parent != null) {
            startParentByNodeIfNeeded(parent, now);
        }
        return parent;
    }

    private void reportFailure(Object descriptorId, Throwable throwable) {
        TestFailure failure = FAILURE_MAPPER.createFailure(throwable);
        resultProcessor.failure(descriptorId, failure);
    }

    @Override
    public void testAssumptionFailure(Failure failure) {
        TestDescriptorInternal testInternal;
        synchronized (lock) {
            testInternal = executing.get(failure.getDescription());
            assumptionFailed.add(failure.getDescription());

            if (testInternal != null) {
                // This is the normal path, we've just seen a test failure
                // for a test that we saw start
                Throwable exception = failure.getException();
                reportAssumptionFailure(testInternal.getId(), exception);
            } else {
                // This can happen when, for example, a @BeforeClass or @AfterClass method fails
                // We generate an artificial start/failure/completed sequence of events
                withPotentiallyMissingParent(className(failure.getDescription()), clock.getCurrentTime(), parentId -> {
                    TestDescriptorInternal child = nullSafeDescriptor(idGenerator.generateId(), failure.getDescription());
                    resultProcessor.started(child, startEvent(parentId));
                    Throwable exception = failure.getException();
                    reportAssumptionFailure(child.getId(), exception);
                    resultProcessor.completed(child.getId(), new TestCompleteEvent(clock.getCurrentTime(), TestResult.ResultType.SKIPPED));
                });
            }
        }
    }

    private void reportAssumptionFailure(Object descriptorId, Throwable throwable) {
        TestFailure assumptionFailure = DefaultTestFailure.fromTestAssumptionFailure(throwable);
        resultProcessor.failure(descriptorId, assumptionFailure);
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
            synchronized (lock) {
                if (postRunStartData == null) {
                    // handleRunFinished has already been called, which means we shouldn't report anything more
                    if (LOGGER.isErrorEnabled()) {
                        LOGGER.error("Test execution failure after test run finished for class " + testClassDefinition.getTestClassName(), failure.getRawFailure());
                    }
                    return;
                }
                long now = clock.getCurrentTime();
                if (executing.isEmpty()) {
                    String testName = testsStarted ? "executionError" : "initializationError";

                    withPotentiallyMissingParent(testClassDefinition.getTestClassName(), now, parentId -> {
                        DefaultTestDescriptor initializationError = new DefaultTestDescriptor(idGenerator.generateId(), testClassDefinition.getTestClassName(), testName);
                        resultProcessor.started(initializationError, new TestStartEvent(now, parentId));
                        resultProcessor.failure(initializationError.getId(), failure);
                        resultProcessor.completed(initializationError.getId(), new TestCompleteEvent(now));
                    });
                } else {
                    executing.forEach((description, testInternal) -> {
                        Object id = testInternal.getId();
                        resultProcessor.failure(id, failure);
                        completeNode(description, id, new TestCompleteEvent(now));
                    });
                }
            }
        } finally {
            handleRunFinished();
        }
    }

    @Override
    public void testIgnored(Description description) {
        synchronized (lock) {
            if (methodName(description) == null) {
                // An @Ignored class, ignore the event. We don't get testIgnored events for each method, so we have
                // generate them on our own
                processIgnoredClass(description);
            } else {
                TestNode parent = startRequiredParentIfNeeded(description);
                TestDescriptorInternal descriptor = descriptor(idGenerator.generateId(), description);
                startNode(description, descriptor, startEvent(parent.requireId()));
                long endTime = clock.getCurrentTime();
                completeNode(description, descriptor.getId(), new TestCompleteEvent(endTime, TestResult.ResultType.SKIPPED));
            }
        }
    }

    private void processIgnoredClass(Description description) {
        // Start the class
        TestNode classNode = getNodeUnambiguously(description);
        if (classNode == null) {
            // Log instead of failing hard, to allow for some level of leniency in case of unexpected JUnit behavior
            LOGGER.info("Could not find test node for ignored class: {}", description);
            return;
        }
        startParentByNodeIfNeeded(classNode, clock.getCurrentTime());
        String className = className(description);
        for (Description childDescription : IgnoredTestDescriptorProvider.getAllDescriptions(description, className)) {
            Object parentId = classNode.resolveId();
            TestDescriptorInternal descriptor = descriptor(idGenerator.generateId(), childDescription);
            resultProcessor.started(descriptor, startEvent(parentId));
            resultProcessor.completed(descriptor.getId(), new TestCompleteEvent(clock.getCurrentTime(), TestResult.ResultType.SKIPPED));
        }
    }

    @Override
    public void testFinished(Description description) {
        long endTime = clock.getCurrentTime();
        TestDescriptorInternal testInternal;
        synchronized (lock) {
            testInternal = executing.remove(description);
            if (testInternal == null) {
                testInternal = executing.getOnlyValue();
                if (testInternal != null) {
                    // Assume that test has renamed itself (this can actually happen)
                    executing.clear();
                }
            }
            if (testInternal == null) {
                // Log instead of failing hard, to allow for some level of leniency in case of unexpected JUnit behavior
                LOGGER.info("Unexpected end event for {}", description);
                return;
            }
            TestResult.ResultType resultType = assumptionFailed.remove(description) ? TestResult.ResultType.SKIPPED : null;
            completeNode(description, testInternal.getId(), new TestCompleteEvent(endTime, resultType));
        }
    }

    private static TestDescriptorInternal descriptor(Object id, Description description) {
        return new DefaultTestDescriptor(id, className(description), methodName(description));
    }

    private static TestDescriptorInternal nullSafeDescriptor(Object id, Description description) {
        String methodName = methodName(description);
        if (methodName != null) {
            return new DefaultTestDescriptor(id, className(description), methodName);
        } else {
            return new DefaultTestDescriptor(id, className(description), "classMethod");
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

    private TestStartEvent startEvent(@Nullable Object parentId) {
        return new TestStartEvent(clock.getCurrentTime(), parentId);
    }

    @Override
    public void testRunStarted(Description description) {
        DescriptionMap<TestNode, TestNode> descToNode = TestNode.createDescriptionMap();
        DescriptionMap<TestNode, TestNode> childDescToParentNode = TestNode.createDescriptionMap();
        addParentIds(description, descToNode, childDescToParentNode);

        synchronized (lock) {
            this.postRunStartData = new PostRunStartData(descToNode, childDescToParentNode);

            // Start root immediately so output is captured for it
            startParentByNodeIfNeeded(Objects.requireNonNull(descToNode.get(description)), clock.getCurrentTime());
        }
    }

    private void addParentIds(
        Description description,
        DescriptionMap<TestNode, TestNode> descToNode,
        DescriptionMap<TestNode, TestNode> childDescToParentNode
    ) {
        TestNode thisNode = new TestNode(idGenerator, description);
        descToNode.put(description, thisNode);
        for (Description child : description.getChildren()) {
            childDescToParentNode.put(child, thisNode);
            if (description.isSuite()) {
                addParentIds(child, descToNode, childDescToParentNode);
            }
        }
    }

    @Override
    public void testRunFinished(Result result) {
        handleRunFinished();
    }

    private void handleRunFinished() {
        synchronized (lock) {
            // Complete any active parents, in reverse order
            long now = clock.getCurrentTime();

            TestNode parent;
            while ((parent = activeParents.pollLast()) != null) {
                Object parentId = parent.requireId();
                resultProcessor.completed(parentId, new TestCompleteEvent(now));
            }

            postRunStartData = null;
            descToStartedCount.clear();
            parentToExecutingAmbiguousChild.clear();
            executingAmbiguousChildren.clear();
            parentsWaitingForAmbiguousChildren.clear();
            executingToChosenParent.clear();
            executing.clear();
            assumptionFailed.clear();
        }
    }
}
