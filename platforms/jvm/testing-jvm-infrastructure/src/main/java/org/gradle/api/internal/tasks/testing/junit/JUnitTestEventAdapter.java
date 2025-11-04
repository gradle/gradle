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
import org.gradle.api.internal.tasks.testing.source.DefaultClassSource;
import org.gradle.api.internal.tasks.testing.source.DefaultMethodSource;
import org.gradle.api.internal.tasks.testing.source.DefaultOtherSource;
import org.gradle.api.tasks.testing.TestFailure;
import org.gradle.api.tasks.testing.TestResult;
import org.gradle.api.tasks.testing.source.TestSource;
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
import java.util.Arrays;
import java.util.Collections;
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

    private static final List<TestFailureMapper> MAPPERS = Arrays.asList(
        new JUnitComparisonTestFailureMapper(),
        new OpenTestAssertionFailedMapper(),
        new OpenTestMultipleFailuresErrorMapper(),
        new AssertjMultipleAssertionsErrorMapper(),
        new AssertErrorMapper()
    );

    private static final class TestNode {
        // Avoid generating the id too early, as it leads to a confusing ordering of ids
        // Ordering generally shouldn't be relied on, but the tests read cleaner if ids are generated in a depth-first order
        private final IdGenerator<?> idGenerator;
        @Nullable
        private volatile Object resolvedId;
        final Description description;

        TestNode(IdGenerator<?> idGenerator, Description description) {
            this.idGenerator = idGenerator;
            this.description = description;
        }

        public Object resolveId() {
            Object localId = resolvedId;
            if (localId == null) {
                synchronized (this) {
                    localId = resolvedId;
                    if (localId == null) {
                        localId = idGenerator.generateId();
                        resolvedId = localId;
                    }
                }
            }
            return localId;
        }
    }

    private static final class PostRunStartData {
        final Map<Description, TestNode> descToNode;
        final Map<Description, TestNode> childDescToParentNode;

        PostRunStartData(
            Map<Description, TestNode> descToNode,
            Map<Description, TestNode> childDescToParentNode
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
    private final Deque<Description> activeParents = new ConcurrentLinkedDeque<>();
    private final Map<Description, TestDescriptorInternal> executing = new HashMap<>();
    private final Set<Description> assumptionFailed = new HashSet<>();
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
     * don't set a name on the root description, e.g. {@link org.junit.internal.runners.JUnit38ClassRunner}.
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
    private TestNode getParentOf(Description description) {
        return requirePostRunStartData().childDescToParentNode.get(description);
    }

    /**
     * Start the parent of the given description if it is not already active, and throw an exception if there is no parent.
     *
     * <p>
     * This method also starts any ancestor nodes that are not already active.
     * </p>
     *
     * @param description the description whose parent should be started
     * @return the id of the parent
     */
    private Object startRequiredParentIfNeeded(Description description) {
        Object parentId = startParentIfNeeded(description);
        if (parentId == null) {
            throw new AssertionError("No parent found for " + description);
        }
        return parentId;
    }

    /**
     * Start the parent of the given description, if it has one, and it is not already active.
     *
     * <p>
     * This method also starts any ancestor nodes that are not already active.
     * </p>
     *
     * @param description the description whose parent should be started
     * @return the id of the parent, or {@code null} if there is no parent
     */
    @Nullable
    private Object startParentIfNeeded(Description description) {
        TestNode parent = getParentOf(description);
        if (parent == null) {
            return null;
        }
        startParentByNodeIfNeeded(parent, clock.getCurrentTime());
        return parent.resolveId();
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
        Object grandparentId = startParentIfNeeded(parent.description);
        synchronized (lock) {
            if (activeParents.contains(parent.description)) {
                return;
            }
            activeParents.addLast(parent.description);
        }
        String rootName = grandparentId == null ? this.rootName : className(parent.description);
        if (rootName == null) {
            throw new AssertionError("No class name found for " + parent.description);
        }
        TestDescriptorInternal parentDescriptor;
        if (grandparentId != null && supportsTestClassMethod() && getTestClassIfPossible(parent.description) == null) {
            // When Description.getTestClass() returns null and we have a grandparent,
            // it indicates "suites" of parameterized tests, or some other kind of synthetic grouping.
            // Avoid treating these as classes.
            parentDescriptor = new DefaultTestSuiteDescriptor(parent.resolveId(), rootName);
        } else {
            parentDescriptor = new DefaultTestClassDescriptor(parent.resolveId(), rootName, classDisplayName(rootName));
        }
        resultProcessor.started(parentDescriptor, new TestStartEvent(now, grandparentId));
    }

    // Note: This is JUnit 4.13+ only, so it may not be called
    // We only use it to provide more exact timing for suites
    // If not called, the suite start time is when the first test starts
    @Override
    public void testSuiteStarted(Description description) {
        testsStarted = true;
        TestNode testNode = requirePostRunStartData().descToNode.get(description);
        if (testNode != null) {
            startParentByNodeIfNeeded(testNode, clock.getCurrentTime());
        }
    }

    // Note: This is JUnit 4.13+ only, so it may not be called
    // We only use it to provide more exact timing for suites
    // If not called, the suite end time is when the whole run finishes
    @Override
    public void testSuiteFinished(Description description) {
        TestNode testNode = requirePostRunStartData().descToNode.get(description);
        if (testNode == null) {
            return;
        }
        synchronized (lock) {
            if (activeParents.remove(description)) {
                // Parent was active, complete it now
                resultProcessor.completed(testNode.resolveId(), new TestCompleteEvent(clock.getCurrentTime()));
            }
        }
    }

    @Override
    public void testStarted(Description description) {
        testsStarted = true;
        Object parentId = startRequiredParentIfNeeded(description);
        TestDescriptorInternal descriptor = methodDescriptor(idGenerator.generateId(), description);
        synchronized (lock) {
            TestDescriptorInternal oldTest = executing.put(description, descriptor);
            assert oldTest == null : String.format("Unexpected start event for %s", description);
        }
        resultProcessor.started(descriptor, startEvent(parentId));
    }

    @Override
    public void testFailure(Failure failure) {
        TestDescriptorInternal testInternal;
        synchronized (lock) {
            testInternal = executing.get(failure.getDescription());
        }

        if (testInternal != null) {
            // This is the normal path, we've just seen a test failure
            // for a test that we saw start
            Throwable exception = failure.getException();
            reportFailure(testInternal.getId(), exception);
        } else {
            // This can happen when, for example, a @BeforeClass or @AfterClass method fails
            // We generate an artificial start/failure/completed sequence of events
            withPotentiallyMissingParent(className(failure.getDescription()), clock.getCurrentTime(), parentId -> {
                TestDescriptorInternal child = methodDescriptor(idGenerator.generateId(), failure.getDescription());
                resultProcessor.started(child, startEvent(parentId));
                Throwable exception = failure.getException();
                reportFailure(child.getId(), exception);
                resultProcessor.completed(child.getId(), new TestCompleteEvent(clock.getCurrentTime()));
            });
        }
    }

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

        action.accept(parent != null ? parent.resolveId() : syntheticParentId);

        if (syntheticParentId != null) {
            resultProcessor.completed(syntheticParentId, new TestCompleteEvent(now));
        }
    }

    @Nullable
    private TestNode startParentMatchingClassName(String className, long now) {
        TestNode parent = null;
        for (Map.Entry<Description, TestNode> entry : requirePostRunStartData().descToNode.entrySet()) {
            if (className.equals(className(entry.getKey()))) {
                parent = entry.getValue();
                startParentByNodeIfNeeded(entry.getValue(), now);
            }
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
        }

        if (testInternal != null) {
            // This is the normal path, we've just seen a test failure
            // for a test that we saw start
            Throwable exception = failure.getException();
            reportAssumptionFailure(testInternal.getId(), exception);
        } else {
            // This can happen when, for example, a @BeforeClass or @AfterClass method fails
            // We generate an artificial start/failure/completed sequence of events
            withPotentiallyMissingParent(className(failure.getDescription()), clock.getCurrentTime(), parentId -> {
                TestDescriptorInternal child = methodDescriptor(idGenerator.generateId(), failure.getDescription());
                resultProcessor.started(child, startEvent(parentId));
                Throwable exception = failure.getException();
                reportAssumptionFailure(child.getId(), exception);
                resultProcessor.completed(child.getId(), new TestCompleteEvent(clock.getCurrentTime(), TestResult.ResultType.SKIPPED));
            });
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
                for (Map.Entry<Description, TestDescriptorInternal> test : executing.entrySet()) {
                    resultProcessor.failure(test.getValue().getId(), failure);
                    resultProcessor.completed(test.getValue().getId(), new TestCompleteEvent(now));
                }
            }
        } finally {
            handleRunFinished();
        }
    }

    @Override
    public void testIgnored(Description description) throws Exception {
        if (methodName(description) == null) {
            // An @Ignored class, ignore the event. We don't get testIgnored events for each method, so we have
            // generate them on our own
            processIgnoredClass(description);
        } else {
            Object parentId = startRequiredParentIfNeeded(description);
            TestDescriptorInternal descriptor = descriptor(idGenerator.generateId(), description);
            resultProcessor.started(descriptor, startEvent(parentId));
            long endTime = clock.getCurrentTime();
            resultProcessor.completed(descriptor.getId(), new TestCompleteEvent(endTime, TestResult.ResultType.SKIPPED));
        }
    }

    private void processIgnoredClass(Description description) {
        // Start the class
        TestNode classNode = requirePostRunStartData().descToNode.get(description);
        if (classNode == null) {
            throw new AssertionError("No class node found for " + description);
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
        TestResult.ResultType resultType;
        synchronized (lock) {
            testInternal = executing.remove(description);
            if (testInternal == null && executing.size() == 1) {
                // Assume that test has renamed itself (this can actually happen)
                testInternal = executing.values().iterator().next();
                executing.clear();
            }
            assert testInternal != null : String.format("Unexpected end event for %s", description);
            resultType = assumptionFailed.remove(description) ? TestResult.ResultType.SKIPPED : null;
        }
        resultProcessor.completed(testInternal.getId(), new TestCompleteEvent(endTime, resultType));
    }

    /**
     * Creates a test descriptor. The descriptor source type is inferred from whether the class and method names can be obtained.
     */
    private static TestDescriptorInternal descriptor(Object id, Description description) {
        String className = className(description);
        String methodName = methodName(description);
        TestSource source;
        if (className != null && methodName != null) {
            source = new DefaultMethodSource(className, methodName);
        } else if (className != null && methodName == null) {
            source = new DefaultClassSource(className);
        } else {
            source = DefaultOtherSource.getInstance();
        }
        return new DefaultTestDescriptor(id, className, methodName, source);
    }

    /**
     * Creates a test descriptor describing a method, even if the method name cannot be obtained.
     */
    private static TestDescriptorInternal methodDescriptor(Object id, Description description) {
        String methodName = methodName(description);
        String className = className(description);
        if (methodName != null) {
            return new DefaultTestDescriptor(id, className, methodName, new DefaultMethodSource(className, methodName));
        } else {
            return new DefaultTestDescriptor(id, className, "classMethod", new DefaultMethodSource(className, "classMethod"));
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
        Map<Description, TestNode> descToNode = new HashMap<>();
        Map<Description, TestNode> childDescToParentNode = new HashMap<>();
        addParentIds(description, descToNode, childDescToParentNode);
        this.postRunStartData = new PostRunStartData(
            Collections.unmodifiableMap(descToNode),
            Collections.unmodifiableMap(childDescToParentNode)
        );

        // Start root immediately so output is captured for it
        startParentByNodeIfNeeded(Objects.requireNonNull(descToNode.get(description)), clock.getCurrentTime());
    }

    private void addParentIds(
        Description description,
        Map<Description, TestNode> descToNode,
        Map<Description, TestNode> childDescToParentNode
    ) {
        TestNode thisNode = new TestNode(idGenerator, description);
        descToNode.put(description, thisNode);
        for (Description child : description.getChildren()) {
            childDescToParentNode.put(child, thisNode);
            if (methodName(child) == null) {
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

            PostRunStartData postRunStartData = requirePostRunStartData();
            Description parent;
            while ((parent = activeParents.pollLast()) != null) {
                Object parentId = Objects.requireNonNull(postRunStartData.descToNode.get(parent)).resolveId();
                resultProcessor.completed(parentId, new TestCompleteEvent(now));
            }

            executing.clear();
            assumptionFailed.clear();
        }
    }
}
