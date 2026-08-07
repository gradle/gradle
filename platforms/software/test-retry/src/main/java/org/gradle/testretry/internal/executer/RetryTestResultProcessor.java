/*
 * Copyright 2023 the original author or authors.
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
package org.gradle.testretry.internal.executer;

import org.gradle.api.internal.tasks.testing.TestCompleteEvent;
import org.gradle.api.internal.tasks.testing.TestDescriptorInternal;
import org.gradle.api.internal.tasks.testing.TestResultProcessor;
import org.gradle.api.internal.tasks.testing.TestStartEvent;
import org.gradle.api.tasks.testing.TestFailure;
import org.gradle.api.tasks.testing.TestOutputEvent;
import org.gradle.testretry.internal.executer.framework.TestFrameworkStrategy;
import org.gradle.testretry.internal.filter.ClassRetryMatcher;
import org.gradle.testretry.internal.filter.RetryFilter;
import org.gradle.testretry.internal.testsreader.TestsReader;
import org.gradle.util.GradleVersion;

import java.io.Closeable;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toMap;
import static org.gradle.api.tasks.testing.TestResult.ResultType.SKIPPED;

final class RetryTestResultProcessor implements TestResultProcessor, Closeable {

    private final TestFrameworkStrategy testFrameworkStrategy;
    private final RetryFilter filter;
    private final ClassRetryMatcher classRetryMatcher;
    private final TestsReader testsReader;
    private final TestResultProcessor delegate;

    private final int maxFailures;
    private final boolean failOnSkippedAfterRetry;
    private boolean lastRetry;
    private boolean hasRetryFilteredFailures;
    private Method failureMethod;

    private final Map<Object, TestDescriptorInternal> activeDescriptorsById = new HashMap<>();
    private final Map<Object, Object> parentIdByDescriptorId = new HashMap<>();

    private final Set<String> testClassesSeenInCurrentRound = new HashSet<>();
    private TestNames currentRoundFailedTests = new TestNames();
    private TestNames previousRoundFailedTests = new TestNames();

    private Object rootTestDescriptorId;
    private TestCompleteEvent rootCompleteEvent;

    RetryTestResultProcessor(
        TestFrameworkStrategy testFrameworkStrategy,
        RetryFilter filter,
        ClassRetryMatcher classRetryMatcher,
        TestsReader testsReader,
        TestResultProcessor delegate,
        int maxFailures,
        boolean failOnSkippedAfterRetry
    ) {
        this.testFrameworkStrategy = testFrameworkStrategy;
        this.filter = filter;
        this.classRetryMatcher = classRetryMatcher;
        this.testsReader = testsReader;
        this.delegate = delegate;
        this.maxFailures = maxFailures;
        this.failOnSkippedAfterRetry = failOnSkippedAfterRetry;
    }

    @Override
    public void close() {
        if (rootCompleteEvent != null) {
            delegate.completed(rootTestDescriptorId, rootCompleteEvent);
            rootTestDescriptorId = null;
            rootCompleteEvent = null;
        }
    }

    @Override
    public void started(TestDescriptorInternal descriptor, TestStartEvent testStartEvent) {
        if (rootTestDescriptorId == null) {
            rootTestDescriptorId = descriptor.getId();
            activeDescriptorsById.put(descriptor.getId(), descriptor);
            delegate.started(descriptor, testStartEvent);
        } else if (!descriptor.getId().equals(rootTestDescriptorId)) {
            activeDescriptorsById.put(descriptor.getId(), descriptor);
            parentIdByDescriptorId.put(descriptor.getId(), testStartEvent.getParentId());
            registerSeenTestClass(descriptor);
            delegate.started(descriptor, testStartEvent);
        }
    }

    @Override
    public void completed(Object testId, TestCompleteEvent testCompleteEvent) {
        if (testId.equals(rootTestDescriptorId)) {
            // nothing failed in the current round, but we have some un-retried tests
            if (currentRoundFailedTests.isEmpty() && !previousRoundFailedTests.isEmpty()) {
                ignoreExpectedUnretriedTests();
            }
            if (lastRun()) {
                rootCompleteEvent = null;
            } else {
                rootCompleteEvent = testCompleteEvent;
                return;
            }
        } else {
            TestDescriptorInternal descriptor = activeDescriptorsById.remove(testId);
            if (descriptor != null && descriptor.getClassName() != null) {
                String className = descriptor.getClassName();
                String name = descriptor.getName();

                boolean failedInPreviousRound = previousRoundFailedTests.remove(className, name);
                boolean shouldRetrySkippedTestThatPreviouslyFailed = failedInPreviousRound && testCompleteEvent.getResultType() == SKIPPED && failOnSkippedAfterRetry;
                if (shouldRetrySkippedTestThatPreviouslyFailed) {
                    addRetry(descriptor);
                }

                // class-level lifecycle failures do not guarantee that all methods that failed in the previous round will be re-executed (e.g. due to class setup failure)
                // in this case, we retry the entire class, so we ignore method-level failures for the next round
                // we keep all lifecycle failures from previous round to make sure we report them as passed later on
                if (isLifecycleFailure(className, name)) {
                    previousRoundFailedTests.remove(className, n -> {
                        if (isLifecycleFailure(className, n)) {
                            currentRoundFailedTests.add(className, n);
                        }
                        return true;
                    });
                }

                if (isClassDescriptor(descriptor)) {
                    previousRoundFailedTests.remove(className, n -> {
                        if (isLifecycleFailure(className, n)) {
                            emitFakePassedEvent(descriptor, testCompleteEvent, n);
                            return true;
                        } else {
                            return false;
                        }
                    });
                }
            }
        }

        delegate.completed(testId, testCompleteEvent);
    }

    private void ignoreExpectedUnretriedTests() {
        // check with the framework implementation if it is expected
        Map<String, Set<String>> expectedUnretriedTests = previousRoundFailedTests.stream()
            .collect(toMap(
                Map.Entry::getKey,
                entry -> entry.getValue().stream()
                    .filter(test -> testFrameworkStrategy.isExpectedUnretriedTest(entry.getKey(), test))
                    .collect(Collectors.toSet())
            ));
        expectedUnretriedTests.forEach((className, tests) -> previousRoundFailedTests.remove(className, tests::contains));
    }

    private boolean isLifecycleFailure(String className, String name) {
        return testFrameworkStrategy.isLifecycleFailureTest(testsReader, className, name);
    }

    private void registerSeenTestClass(TestDescriptorInternal descriptor) {
        String maybeTestClassName = descriptor.getClassName();

        if (maybeTestClassName != null && !maybeTestClassName.isEmpty()) {
            testClassesSeenInCurrentRound.add(maybeTestClassName);
        }
    }

    private void addRetry(TestDescriptorInternal descriptor) {
        Optional<TestDescriptorInternal> classMatchingClassRetryFilter = firstClassMatchingClassRetryFilter(descriptor);
        if (classMatchingClassRetryFilter.isPresent()) {
            currentRoundFailedTests.addClass(classMatchingClassRetryFilter.get().getClassName());
        } else {
            currentRoundFailedTests.add(descriptor.getClassName(), descriptor.getName());
        }
    }

    private Optional<TestDescriptorInternal> firstClassMatchingClassRetryFilter(TestDescriptorInternal descriptor) {
        // top-level descriptor describes a test worker which cannot match the class retry filter
        Object parentId = parentIdByDescriptorId.get(descriptor.getId());
        if (parentId == null) {
            return Optional.empty();
        }

        // if the parent is not tracked for any reason, then it also cannot match the class retry filter
        TestDescriptorInternal parentDescriptor = activeDescriptorsById.get(parentId);
        if (parentDescriptor == null) {
            return Optional.empty();
        }

        // check if any of the parent classes matches the class retry filter
        Optional<TestDescriptorInternal> parentClassToRetryEntirely = firstClassMatchingClassRetryFilter(parentDescriptor);
        if (parentClassToRetryEntirely.isPresent()) {
            return parentClassToRetryEntirely;
        }

        // check if the class on the current level matches the class retry filter
        String className = descriptor.getClassName();
        if (className != null && classRetryMatcher.retryWholeClass(className)) {
            return Optional.of(descriptor);
        }

        // no classes in the descriptor hierarchy should be retried as a whole
        return Optional.empty();
    }

    private void emitFakePassedEvent(TestDescriptorInternal parent, TestCompleteEvent parentEvent, String name) {
        Object syntheticTestId = new Object();
        TestDescriptorInternal syntheticDescriptor = new TestDescriptorImpl(syntheticTestId, parent, name);
        long timestamp = parentEvent.getEndTime();
        delegate.started(syntheticDescriptor, new TestStartEvent(timestamp, parent.getId()));
        delegate.completed(syntheticTestId, new TestCompleteEvent(timestamp));
    }

    private boolean isClassDescriptor(TestDescriptorInternal descriptor) {
        return descriptor.getClassName() != null && descriptor.getClassName().equals(descriptor.getName());
    }

    @Override
    public void output(Object testId, TestOutputEvent testOutputEvent) {
        delegate.output(testId, testOutputEvent);
    }

    @SuppressWarnings("unused")
    public void failure(Object testId, Throwable throwable) {
        // Gradle 7.6 changed the method signature from failure(Object, Throwable) to failure(Object, TestFailure).
        // To maintain compatibility with older versions, the original method needs to exist and needs to call failure()
        // on the delegate via reflection.
        failure(testId);
        try {
            Method failureMethod = lookupFailureMethod();
            failureMethod.invoke(delegate, testId, throwable);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private Method lookupFailureMethod() throws ReflectiveOperationException {
        if (failureMethod == null) {
            failureMethod = delegate.getClass().getMethod("failure", Object.class, Throwable.class);
        }
        return failureMethod;
    }

    @Override
    public void failure(Object testId, TestFailure result) {
        failure(testId);
        delegate.failure(testId, result);
    }

    private void failure(Object testId) {
        final TestDescriptorInternal descriptor = activeDescriptorsById.get(testId);
        if (descriptor != null) {
            String className = descriptor.getClassName();
            if (className != null && !className.isEmpty()) {
                if (filter.canRetry(className)) {
                    addRetry(descriptor);
                } else {
                    hasRetryFilteredFailures = true;
                }
            } else if (isLifecycleFailure(descriptor.getClassName(), descriptor.getName())){
                addRetry(descriptor);
            }
        }
    }

    private boolean lastRun() {
        return currentRoundFailedTests.isEmpty()
            || hasNonRetriedTests()
            || lastRetry
            || currentRoundFailedTestsExceedsMaxFailures();
    }

    private boolean hasNonRetriedTests() {
        return !cleanedUpFailedTestsOfPreviousRound().isEmpty();
    }

    private boolean currentRoundFailedTestsExceedsMaxFailures() {
        return maxFailures > 0 && currentRoundFailedTests.size() >= maxFailures;
    }

    public RoundResult getResult() {
        return new RoundResult(
            currentRoundFailedTests,
            cleanedUpFailedTestsOfPreviousRound(),
            lastRun(),
            hasRetryFilteredFailures,
            testClassesSeenInCurrentRound
        );
    }

    /**
     * When running tests via the JUnit's suite engine or using {@code @Nested} test classes,
     * Gradle 5.0 does not report the intermediate test class nodes. This leads to a problem when such
     * test classes are configured to be retried on class-level, as we cannot properly remove them
     * from the previous round of failed tests without a proper test event from Gradle.
     * <p/>
     * For Gradle 5.0, we manually remove all test classes with no registers test methods,
     * if we saw the test class during this round. We assume here, that those entries are
     * just here because of the missing event from Gradle for the intermediate node.
     * <p/>
     * For Gradle version 5.1 and above we don't do this, as we expect events for those intermediate
     * nodes.
     * <p/>
     * This solution is not perfect but still allows users to use classRetry with Gradle 5
     * together with the suite engine and/or nested test classes.
     *
     * @return cleaned up failed test names of previous round
     */
    private TestNames cleanedUpFailedTestsOfPreviousRound() {
        boolean isGradle50 = GradleVersion.current().getBaseVersion().equals(GradleVersion.version("5.0"));

        if (isGradle50 && !testClassesSeenInCurrentRound.isEmpty() && previousRoundFailedTests.hasClassesWithoutTestNames()) {
            TestNames testNames = new TestNames();
            previousRoundFailedTests.stream().forEach(entry -> {
                String testClass = entry.getKey();
                Set<String> testMethods = entry.getValue();

                if (testMethods.isEmpty()) {
                    if (!testClassesSeenInCurrentRound.contains(testClass)) {
                        testNames.addClass(testClass);
                    }
                } else {
                    testNames.addAll(testClass, testMethods);
                }
            });

            return testNames;
        }

        return previousRoundFailedTests;
    }

    public void reset(boolean lastRetry) {
        if (lastRun()) {
            throw new IllegalStateException("processor has completed");
        }

        this.lastRetry = lastRetry;
        this.testClassesSeenInCurrentRound.clear();
        this.previousRoundFailedTests = currentRoundFailedTests;
        this.currentRoundFailedTests = new TestNames();
        this.activeDescriptorsById.clear();
        this.parentIdByDescriptorId.clear();
    }

}
