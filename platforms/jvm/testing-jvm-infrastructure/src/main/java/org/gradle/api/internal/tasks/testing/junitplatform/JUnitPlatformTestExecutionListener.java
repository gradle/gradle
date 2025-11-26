/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.api.internal.tasks.testing.junitplatform;

import org.gradle.api.internal.tasks.testing.DefaultNestedTestSuiteDescriptor;
import org.gradle.api.internal.tasks.testing.DefaultParameterizedTestDescriptor;
import org.gradle.api.internal.tasks.testing.DefaultTestClassDescriptor;
import org.gradle.api.internal.tasks.testing.DefaultTestDescriptor;
import org.gradle.api.internal.tasks.testing.DefaultTestFailure;
import org.gradle.api.internal.tasks.testing.DefaultTestFileAttachmentDataEvent;
import org.gradle.api.internal.tasks.testing.DefaultTestKeyValueDataEvent;
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
import org.gradle.api.tasks.testing.TestResult.ResultType;
import org.gradle.internal.MutableBoolean;
import org.gradle.internal.id.CompositeIdGenerator;
import org.gradle.internal.id.IdGenerator;
import org.gradle.internal.time.Clock;
import org.gradle.util.internal.TextUtil;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.TestSource;
import org.junit.platform.engine.UniqueId;
import org.junit.platform.engine.reporting.FileEntry;
import org.junit.platform.engine.reporting.ReportEntry;
import org.junit.platform.engine.support.descriptor.ClassSource;
import org.junit.platform.engine.support.descriptor.FileSource;
import org.junit.platform.engine.support.descriptor.MethodSource;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;

import static org.gradle.api.tasks.testing.TestResult.ResultType.SKIPPED;
import static org.junit.platform.engine.TestExecutionResult.Status.ABORTED;
import static org.junit.platform.engine.TestExecutionResult.Status.FAILED;

/**
 * A {@link TestExecutionListener} that maps JUnit5 events to Gradle test events.
 * Most importantly, it will map assertion and platform failures to Gradle's {@link TestFailure} class, which we can send through the TAPI.
 */
@NullMarked
public class JUnitPlatformTestExecutionListener implements TestExecutionListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(JUnitPlatformTestExecutionListener.class);

    private final static List<TestFailureMapper> MAPPERS = Arrays.asList(
        new OpenTestAssertionFailedMapper(),
        new OpenTestMultipleFailuresErrorMapper(),
        new JUnitComparisonTestFailureMapper(),
        new AssertjMultipleAssertionsErrorMapper(),
        new AssertErrorMapper()
    );

    private static final DefaultThrowableToTestFailureMapper FAILURE_MAPPER = new DefaultThrowableToTestFailureMapper(MAPPERS);

    /**
     * Tracks if {@code getUniqueIdObject()} method exists in the current classloader.
     *
     * The method was added in JUnit Platform 1.8, so won't exist in earlier versions that might be the ones we're
     * using at runtime here.
     */
    private static final boolean HAS_GET_UNIQUE_ID_OBJECT_METHOD = Arrays.stream(TestIdentifier.class.getMethods())
        .anyMatch(method -> method.getName().equals("getUniqueIdObject"));

    private static UniqueId.Segment getLastUniqueIdSegment(TestIdentifier testIdentifier) {
        UniqueId uniqueIdObject = HAS_GET_UNIQUE_ID_OBJECT_METHOD
            ? testIdentifier.getUniqueIdObject()
            : UniqueId.parse(testIdentifier.getUniqueId());
        List<UniqueId.Segment> segments = uniqueIdObject.getSegments();
        // No need to check, guaranteed to have at least one segment
        return segments.get(segments.size() - 1);
    }

    /**
     * Determines if the given TestIdentifier represents the test engine.
     *
     * @param testIdentifier the identifier to check
     * @return {@code true} if the TestIdentifier represents the test engine; {@code false} otherwise
     */
    private static boolean isEngineNode(TestIdentifier testIdentifier) {
        String lastSegmentType = getLastUniqueIdSegment(testIdentifier).getType();
        return "engine".equals(lastSegmentType);
    }

    private final ConcurrentMap<String, TestDescriptorInternal> descriptorsByUniqueId = new ConcurrentHashMap<>();
    private final TestResultProcessor resultProcessor;
    private final Clock clock;
    private final IdGenerator<?> idGenerator;
    private final File baseDefinitionsDir;

    @Nullable
    private TestPlan currentTestPlan;

    public JUnitPlatformTestExecutionListener(TestResultProcessor resultProcessor, Clock clock, IdGenerator<?> idGenerator, File baseDefinitionsDir) {
        this.resultProcessor = resultProcessor;
        this.clock = clock;
        this.idGenerator = idGenerator;
        this.baseDefinitionsDir = baseDefinitionsDir;
    }

    @Override
    public void reportingEntryPublished(TestIdentifier testIdentifier, ReportEntry entry) {
        // JUnit Platform will emit ReportEntry before a test starts if the ReportEntry is published from the class constructor.
        if (wasStarted(testIdentifier)) {
            resultProcessor.published(getId(testIdentifier), new DefaultTestKeyValueDataEvent(convertToInstant(entry.getTimestamp()), entry.getKeyValuePairs()));
        } else {
            // The test has not started yet, so see if we can find a close ancestor and associate the ReportEntry with it
            Object closestStartedAncestor = getIdOfClosestStartedAncestor(testIdentifier);
            if (closestStartedAncestor != null) {
                resultProcessor.published(closestStartedAncestor, new DefaultTestKeyValueDataEvent(convertToInstant(entry.getTimestamp()), entry.getKeyValuePairs()));
            }
            // otherwise, we don't know what to associate this ReportEntry with
            LOGGER.debug("report entry published for unknown test identifier {}", testIdentifier);
        }
    }

    private static Instant convertToInstant(LocalDateTime dateTime) {
        return dateTime.atZone(ZoneId.systemDefault()).toInstant();
    }

    @Override
    public void fileEntryPublished(TestIdentifier testIdentifier, FileEntry entry) {
        // media type can be null if the file is a directory
        String mediaType = entry.getMediaType().orElse(null);

        // JUnit Platform will emit FileEntry before a test starts if the FileEntry is published from the class constructor.
        if (wasStarted(testIdentifier)) {
            resultProcessor.published(getId(testIdentifier), new DefaultTestFileAttachmentDataEvent(convertToInstant(entry.getTimestamp()), entry.getPath().toAbsolutePath(), mediaType));
        } else {
            // The test has not started yet, so see if we can find a close ancestor and associate the FileEntry with it
            Object closestStartedAncestor = getIdOfClosestStartedAncestor(testIdentifier);
            if (closestStartedAncestor != null) {
                resultProcessor.published(closestStartedAncestor, new DefaultTestFileAttachmentDataEvent(convertToInstant(entry.getTimestamp()), entry.getPath().toAbsolutePath(), mediaType));
            }
            // otherwise, we don't know what to associate this FileEntry with
        }
    }

    @Override
    public void testPlanExecutionStarted(TestPlan testPlan) {
        this.currentTestPlan = testPlan;
    }

    @Override
    public void testPlanExecutionFinished(TestPlan testPlan) {
        this.currentTestPlan = null;
        this.descriptorsByUniqueId.clear();
    }

    @Override
    public void executionSkipped(TestIdentifier testIdentifier, String reason) {
        executionSkipped(testIdentifier);
    }

    private void executionSkipped(TestIdentifier testIdentifier) {
        executionStarted(testIdentifier);
        reportSkipped(testIdentifier);
    }

    @Override
    public void executionStarted(TestIdentifier testIdentifier) {
        // The root node will be "JUnit Jupiter" which isn't expected
        // to be seen as a "real" test suite in many tests, so this
        // test is to make sure we're at least under this event
        // Also give the same treatment to the "engine" segments

        if (testIdentifier.getParentId().isPresent() && !isEngineNode(testIdentifier)) {
            reportStartedUnlessAlreadyStarted(testIdentifier);
        }
    }

    @Override
    public void executionFinished(TestIdentifier testIdentifier, TestExecutionResult testExecutionResult) {
        if (testExecutionResult.getStatus() == ABORTED) {
            testExecutionResult.getThrowable().ifPresent(throwable -> resultProcessor.failure(getId(testIdentifier), DefaultTestFailure.fromTestAssumptionFailure(throwable)));
            reportSkipped(testIdentifier);
            return;
        }
        if (testExecutionResult.getStatus() == FAILED) {
            reportStartedUnlessAlreadyStarted(testIdentifier);
            Throwable failure = testExecutionResult.getThrowable().orElseGet(() -> new AssertionError("test failed but did not report an exception"));
            if (testIdentifier.isTest()) {
                reportTestFailure(testIdentifier, failure);
            } else {
                TestDescriptorInternal syntheticTestDescriptor = createSyntheticTestDescriptorForContainer(testIdentifier);
                resultProcessor.started(syntheticTestDescriptor, startEvent(getId(testIdentifier)));
                resultProcessor.failure(syntheticTestDescriptor.getId(), TestFailure.fromTestFrameworkFailure(failure));
                resultProcessor.completed(syntheticTestDescriptor.getId(), completeEvent());
            }
        }
        if (wasStarted(testIdentifier)) {
            resultProcessor.completed(getId(testIdentifier), completeEvent());
        }
    }

    private void reportTestFailure(TestIdentifier testIdentifier, Throwable failure) {
        TestFailure testFailure = FAILURE_MAPPER.createFailure(failure);
        resultProcessor.failure(getId(testIdentifier), testFailure);
    }

    private void reportStartedUnlessAlreadyStarted(TestIdentifier testIdentifier) {
        boolean wasNotAlreadyStarted = createDescriptorIfAbsent(testIdentifier);
        // guard against edge cases (e.g. JUnit 4 classes with custom runners that report the class as ignored after reporting it as started)
        if (wasNotAlreadyStarted) {
            TestDescriptorInternal descriptor = descriptorsByUniqueId.get(testIdentifier.getUniqueId());
            resultProcessor.started(descriptor, startEvent(testIdentifier));
        }
    }

    private void reportSkipped(TestIdentifier testIdentifier) {
        Objects.requireNonNull(currentTestPlan);
        currentTestPlan.getChildren(testIdentifier).stream()
            .filter(child -> !wasStarted(child))
            .forEach(this::executionSkipped);
        if (testIdentifier.isTest()) {
            resultProcessor.completed(getId(testIdentifier), completeEvent(SKIPPED));
        } else if (hasClassSource(testIdentifier)) {
            resultProcessor.completed(getId(testIdentifier), completeEvent());
        }
    }

    private TestStartEvent startEvent(TestIdentifier testIdentifier) {
        Object idOfClosestStartedAncestor = getIdOfClosestStartedAncestor(testIdentifier);
        return startEvent(idOfClosestStartedAncestor);
    }

    @Nullable
    private Object getIdOfClosestStartedAncestor(TestIdentifier testIdentifier) {
        return getAncestors(testIdentifier).stream()
            .map(TestIdentifier::getUniqueId)
            .filter(descriptorsByUniqueId::containsKey)
            .findFirst()
            .map(descriptorsByUniqueId::get)
            .map(TestDescriptorInternal::getId)
            .orElse(null);
    }

    private TestStartEvent startEvent(@Nullable Object parentId) {
        return new TestStartEvent(clock.getCurrentTime(), parentId);
    }

    private TestCompleteEvent completeEvent() {
        return completeEvent(null);
    }

    private TestCompleteEvent completeEvent(@Nullable ResultType resultType) {
        return new TestCompleteEvent(clock.getCurrentTime(), resultType);
    }

    private boolean wasStarted(TestIdentifier testIdentifier) {
        return descriptorsByUniqueId.containsKey(testIdentifier.getUniqueId());
    }

    private boolean createDescriptorIfAbsent(TestIdentifier node) {
        MutableBoolean wasCreated = new MutableBoolean(false);
        descriptorsByUniqueId.computeIfAbsent(node.getUniqueId(), uniqueId -> {
            wasCreated.set(true);
            boolean isTestClassId = isTestClassIdentifier(node);
            if (node.getType().isContainer() || isTestClassId) {
                if (isTestClassId) {
                    return createTestContainerDescriptor(node);
                }
                String displayName = node.getDisplayName();
                Optional<TestDescriptorInternal> parentId = node.getParentId().map(descriptorsByUniqueId::get);
                if (parentId.isPresent()) {
                    Object candidateId = parentId.get().getId();
                    if (candidateId instanceof CompositeIdGenerator.CompositeId) {
                        return createNestedTestSuite(node, displayName, (CompositeIdGenerator.CompositeId) candidateId);
                    }
                }
            }
            // Check for isContainer first
            // Some nodes may be CONTAINER_AND_TEST, and we need to treat them as containers
            if (node.getType().isContainer()) {
                return createTestContainerDescriptor(node);
            } else if (node.getType().isTest()) {
                return createTestDescriptor(node, node.getLegacyReportingName(), node.getDisplayName());
            } else {
                throw new IllegalStateException("Unknown TestIdentifier type: " + node.getType());
            }
        });
        return wasCreated.get();
    }

    private DefaultTestSuiteDescriptor createNestedTestSuite(TestIdentifier node, String displayName, CompositeIdGenerator.CompositeId candidateId) {
        Optional<MethodSource> methodSource = getMethodSource(node);
        if (methodSource.isPresent()) {
            TestDescriptorInternal parentDescriptor = findTestParentDescriptor(node);
            String className = determineClassName(node, parentDescriptor);
            return new DefaultParameterizedTestDescriptor(idGenerator.generateId(), node.getLegacyReportingName(), className, displayName, candidateId);
        } else {
            return new DefaultNestedTestSuiteDescriptor(idGenerator.generateId(), node.getLegacyReportingName(), displayName, candidateId);
        }
    }

    private DefaultTestClassDescriptor createTestContainerDescriptor(TestIdentifier node) {
        String name = extractClassOrResourceName(node);
        String classDisplayName = node.getDisplayName();
        return new DefaultTestClassDescriptor(idGenerator.generateId(), name, classDisplayName);
    }

    private TestDescriptorInternal createSyntheticTestDescriptorForContainer(TestIdentifier node) {
        assert currentTestPlan != null;
        boolean testsStarted = currentTestPlan.getDescendants(node).stream().anyMatch(this::wasStarted);
        String name = testsStarted ? "executionError" : "initializationError";
        return createTestDescriptor(node, name, name);
    }

    private TestDescriptorInternal createTestDescriptor(TestIdentifier test, String name, String displayName) {
        TestDescriptorInternal parentDescriptor = findTestParentDescriptor(test);
        String className = determineClassName(test, parentDescriptor);
        String classDisplayName = determineClassDisplayName(test, parentDescriptor);
        return new DefaultTestDescriptor(idGenerator.generateId(), className, name, classDisplayName, displayName);
    }

    private String determineClassName(TestIdentifier node, @Nullable TestDescriptorInternal parentDescriptor) {
        return determineName(node, parentDescriptor, TestDescriptorInternal::getName);
    }

    private String determineClassDisplayName(TestIdentifier node, @Nullable TestDescriptorInternal parentDescriptor) {
        return determineName(node, parentDescriptor, TestDescriptorInternal::getClassDisplayName);
    }

    private String determineName(TestIdentifier node, @Nullable TestDescriptorInternal parentDescriptor, Function<TestDescriptorInternal, @Nullable String> nameGetter) {
        TestSource source = node.getSource().orElse(null);
        if (source instanceof ClassSource || source instanceof MethodSource) {
            if (parentDescriptor == null) {
                return JUnitPlatformSupport.UNKNOWN_CLASS;
            } else {
                String result = nameGetter.apply(parentDescriptor);
                return result != null ? result : JUnitPlatformSupport.UNKNOWN;
            }
        } else {
            return JUnitPlatformSupport.NON_CLASS;
        }
    }

    private Object getId(TestIdentifier testIdentifier) {
        return descriptorsByUniqueId.get(testIdentifier.getUniqueId()).getId();
    }

    private Set<TestIdentifier> getAncestors(TestIdentifier testIdentifier) {
        Set<TestIdentifier> result = new LinkedHashSet<>();
        Optional<TestIdentifier> parent = getParent(testIdentifier);
        while (parent.isPresent()) {
            result.add(parent.get());
            parent = getParent(parent.get());
        }
        return result;
    }

    @Nullable
    private TestIdentifier findTestClassIdentifier(TestIdentifier testIdentifier) {
        // For tests in default method of interface,
        // we might not be able to get the implementation class directly.
        // In this case, we need to retrieve test plan to get the real implementation class.
        return findInAncestors(
            testIdentifier,
            identifier -> isTestClassIdentifier(identifier) ? identifier : null
        );
    }

    @Nullable
    private TestDescriptorInternal findTestParentDescriptor(TestIdentifier testIdentifier) {
        // First do a search for test classes, to match old behavior
        TestIdentifier classIdentifier = findTestClassIdentifier(testIdentifier);
        if (classIdentifier != null) {
            return descriptorsByUniqueId.get(classIdentifier.getUniqueId());
        }
        // Otherwise just return the first existing ancestor descriptor
        return findInAncestors(
            testIdentifier,
            identifier -> descriptorsByUniqueId.get(identifier.getUniqueId())
        );
    }

    @Nullable
    private <T> T findInAncestors(TestIdentifier testIdentifier, Function<TestIdentifier, @Nullable T> mapper) {
        TestIdentifier current = testIdentifier;
        // Once we hit an engine node, we stop searching, as any class above that is not relevant to the current test.
        while (current != null && !isEngineNode(current)) {
            T result = mapper.apply(current);
            if (result != null) {
                return result;
            }
            current = getParent(current).orElse(null);
        }
        return null;
    }

    @SuppressWarnings("deprecation")
    private Optional<TestIdentifier> getParent(TestIdentifier testIdentifier) {
        Objects.requireNonNull(currentTestPlan);
        try {
            return testIdentifier.getParentIdObject().map(currentTestPlan::getTestIdentifier);
        // Some versions of the JDK throw a BootstrapMethodError
        } catch (NoSuchMethodError | BootstrapMethodError ignore) {
            // To support pre-1.10 versions of the JUnit Platform
            return testIdentifier.getParentId().map(currentTestPlan::getTestIdentifier);
        }
    }

    private boolean isTestClassIdentifier(TestIdentifier testIdentifier) {
        return hasClassSource(testIdentifier) && hasDifferentSourceThanAncestor(testIdentifier);
    }

    private String extractClassOrResourceName(TestIdentifier node) {
        TestIdentifier testClassIdentifier = findTestClassIdentifier(node);
        if (testClassIdentifier != null) {
            Optional<ClassSource> classSource = getClassSource(testClassIdentifier);
            if (classSource.isPresent()) {
                return classSource.get().getClassName();
            }
        }

        if (hasFileSource(node)) {
            Optional<String> fileSourceName = computeNameForFileBasedTest(node);
            if (fileSourceName.isPresent()) {
                return fileSourceName.get();
            }
        }

        // Fall back to the unique id of the node.
        // This prevents duplicate class names that our report can't handle,
        // and provides appropriate information for non-class-based testing.
        UniqueId.Segment lastSegment = getLastUniqueIdSegment(node);
        // Remove ':' as we use them in Paths for reporting
        return (lastSegment.getType() + "_" + lastSegment.getValue()).replace(':', '_');
    }

    /**
     * Computes the relative path from the project root to the source file of the given test identifier.
     *
     * @param node the test identifier whose source file path is to be computed, <strong>MUST</strong> possess a {@link FileSource}
     * @return the relative path from the project root to the source file, or {@link Optional#empty()} if the path could not be computed
     */
    private Optional<String> computeNameForFileBasedTest(TestIdentifier node) {
        Object source = node.getSource().orElse(null);
        if (!(source instanceof FileSource)) {
            throw new IllegalArgumentException("Node source must be a FileSource, was: " + source);
        }

        try {
            Path rootDirPath = baseDefinitionsDir.toPath().toRealPath();
            Path testDefPath = ((FileSource) source).getFile().toPath().toRealPath();
            String relativePath = TextUtil.normaliseFileSeparators(rootDirPath.relativize(testDefPath).toString());
            return Optional.of(relativePath);
        } catch (IOException e) {
            LOGGER.warn("Could not compute relative path to source file for test identifier {}", node, e);
            return Optional.empty();
        }
    }

    private static boolean hasClassSource(TestIdentifier testIdentifier) {
        return getClassSource(testIdentifier).isPresent();
    }

    private static Optional<ClassSource> getClassSource(TestIdentifier testIdentifier) {
        return testIdentifier.getSource()
            .filter(source -> source instanceof ClassSource)
            .map(source -> (ClassSource) source);
    }

    private static Optional<MethodSource> getMethodSource(TestIdentifier testIdentifier) {
        return testIdentifier.getSource()
            .filter(source -> source instanceof MethodSource)
            .map(source -> (MethodSource) source);
    }

    private static boolean hasFileSource(TestIdentifier testIdentifier) {
        return getFileSource(testIdentifier).isPresent();
    }

    private static Optional<FileSource> getFileSource(TestIdentifier testIdentifier) {
        return testIdentifier.getSource()
            .filter(source -> source instanceof FileSource)
            .map(source -> (FileSource) source);
    }

    private boolean hasDifferentSourceThanAncestor(TestIdentifier testIdentifier) {
        Objects.requireNonNull(currentTestPlan);

        Optional<TestIdentifier> parent = currentTestPlan.getParent(testIdentifier);
        while (parent.isPresent()) {
            if (Objects.equals(parent.get().getSource(), testIdentifier.getSource())) {
                return false;
            }
            parent = currentTestPlan.getParent(parent.get());
        }
        return true;
    }

}
