package org.gradle.api.internal.tasks.testing.junit5;

import org.gradle.api.internal.tasks.testing.*;
import org.gradle.api.internal.tasks.testing.junit.JUnitSpec;
import org.gradle.api.tasks.testing.TestResult;
import org.gradle.internal.actor.Actor;
import org.gradle.internal.actor.ActorFactory;
import org.gradle.internal.id.IdGenerator;
import org.gradle.internal.time.Clock;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.TestSource;
import org.junit.platform.engine.reporting.ReportEntry;
import org.junit.platform.engine.support.descriptor.ClassSource;
import org.junit.platform.engine.support.descriptor.MethodSource;
import org.junit.platform.launcher.*;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

public class JUnitPlatformTestClassProcessor implements TestClassProcessor {
    private final ActorFactory actorFactory;
    private final Clock clock;
    private Launcher launcher;
    private Actor resultProcessorActor;

    public JUnitPlatformTestClassProcessor(ActorFactory actorFactory, Clock clock) {
        this.actorFactory = actorFactory;
        this.clock = clock;
    }

    @Override
    public void startProcessing(TestResultProcessor resultProcessor) {
        resultProcessorActor = actorFactory.createBlockingActor(resultProcessor);
        TestResultProcessor threadSafeResultProcessor = resultProcessorActor.getProxy(TestResultProcessor.class);
        this.launcher = LauncherFactory.create();
        launcher.registerTestExecutionListeners(new GradleTestExecutionListener(threadSafeResultProcessor, clock));
    }

    @Override
    public void processTestClass(TestClassRunInfo testClass) {
        System.out.println("Start processing test class: " + testClass.getTestClassName());
        LauncherDiscoveryRequest request = LauncherDiscoveryRequestBuilder.request()
            .selectors(selectClass(testClass.getTestClassName()))
            .build();
        launcher.execute(request);
        System.out.println("End processing test class: " + testClass.getTestClassName());
    }

    @Override
    public void stop() {
        resultProcessorActor.stop();
    }

    private static class GradleTestExecutionListener implements TestExecutionListener {
        private Map<String, TestDescriptorInternal> descriptorCache = new ConcurrentHashMap<String, TestDescriptorInternal>();
        private final TestResultProcessor resultProcessor;
        private final Clock clock;

        public GradleTestExecutionListener(TestResultProcessor resultProcessor, Clock clock) {
            this.resultProcessor = resultProcessor;
            this.clock = clock;
        }

        @Override
        public void testPlanExecutionStarted(TestPlan testPlan) {
            System.out.println("Test plan started: " + testPlan.countTestIdentifiers(new Predicate<TestIdentifier>() {
                @Override
                public boolean test(TestIdentifier testIdentifier) {
                    return true;
                }
            }));
            // TODO ignore for now
        }

        @Override
        public void testPlanExecutionFinished(TestPlan testPlan) {
            System.out.println("Test plan ended.");
            // TODO ignore for now
        }

        @Override
        public void dynamicTestRegistered(TestIdentifier testIdentifier) {
            System.out.println("Dynamic test: " + testIdentifier.getUniqueId());
            // TODO ignore for now
        }

        @Override
        public void executionSkipped(TestIdentifier testIdentifier, String reason) {
            System.out.println("Test skipped: " + testIdentifier.getUniqueId());

            // Gradle seems to only allow two tiers of tests
            if (!testIdentifier.getParentId().isPresent()) {
                return;
            }

            TestDescriptorInternal test = getDescriptor(testIdentifier);
            resultProcessor.completed(test.getId(), new TestCompleteEvent(clock.getCurrentTime(), TestResult.ResultType.SKIPPED));
        }

        @Override
        public void executionStarted(TestIdentifier testIdentifier) {
            System.out.println("Test started: " + testIdentifier.getUniqueId());

            // Gradle seems to only allow two tiers of tests
            if (!testIdentifier.getParentId().isPresent()) {
                return;
            }

            TestDescriptorInternal test = getDescriptor(testIdentifier);
            Object parentId = test.getParent() == null ? null : test.getParent().getId();
            resultProcessor.started(test, new TestStartEvent(clock.getCurrentTime(), parentId));
        }

        @Override
        public void executionFinished(final TestIdentifier testIdentifier, TestExecutionResult testExecutionResult) {
            System.out.println("Test completed: " + testIdentifier.getUniqueId() + " " + testExecutionResult.getStatus());

            // Gradle seems to only allow two tiers of tests
            if (!testIdentifier.getParentId().isPresent()) {
                return;
            }

            final TestDescriptorInternal test = getDescriptor(testIdentifier);
            TestResult.ResultType result;
            switch (testExecutionResult.getStatus()) {
                case SUCCESSFUL:
                    result = TestResult.ResultType.SUCCESS;
                    break;
                case FAILED:
                    result = TestResult.ResultType.FAILURE;
                    break;
                case ABORTED:
                    result = TestResult.ResultType.FAILURE;
                    break;
                default:
                    throw new AssertionError("Invalid Status: " + testExecutionResult.getStatus());
            }

            testExecutionResult.getThrowable().ifPresent(new Consumer<Throwable>() {
                @Override
                public void accept(Throwable throwable) {
                    resultProcessor.failure(test.getId(), throwable);
                }
            });

            resultProcessor.completed(test.getId(), new TestCompleteEvent(clock.getCurrentTime(), result));
        }

        @Override
        public void reportingEntryPublished(TestIdentifier testIdentifier, ReportEntry entry) {
            System.out.println("Test reporting: " + testIdentifier.getUniqueId() + " " + entry);
            // TODO ignore for now
        }

        private TestDescriptorInternal getDescriptor(final TestIdentifier test) {
            return descriptorCache.computeIfAbsent(test.getUniqueId(), new Function<String, TestDescriptorInternal>() {
                @Override
                public TestDescriptorInternal apply(String s) {
                    TestDescriptorInternal parent = test.getParentId().map(new Function<String, TestDescriptorInternal>() {
                        @Override
                        public TestDescriptorInternal apply(String s) {
                            return descriptorCache.get(s);
                        }
                    }).orElse(null);
                    return new JUnitPlatformTestDescriptor(test, parent);
                }
            });
        }
    }

    private static class JUnitPlatformTestDescriptor implements TestDescriptorInternal {
        private final TestDescriptorInternal parent;
        private final Object id;
        private final String name;
        private final String className;
        private final boolean composite;

        public JUnitPlatformTestDescriptor(TestIdentifier test, TestDescriptorInternal parent) {
            this.parent = parent;
            this.id = test.getUniqueId();
            this.name = test.getLegacyReportingName();
            this.composite = test.isContainer();

            if (test.getSource().isPresent()) {
                TestSource rawSource = test.getSource().get();
                if (rawSource instanceof MethodSource) {
                    MethodSource source = (MethodSource) rawSource;
                    this.className = source.getClassName();
                } else if (rawSource instanceof ClassSource) {
                    ClassSource source = (ClassSource) rawSource;
                    this.className = source.getClassName();
                } else {
                    this.className = null;
                }
            } else {
                this.className = null;
            }

        }

        @Nullable
        @Override
        public TestDescriptorInternal getParent() {
            return parent;
        }

        @Override
        public Object getId() {
            return id;
        }

        @Nullable
        @Override
        public Object getOwnerBuildOperationId() {
            return null;
        }

        @Override
        public String getName() {
            return name;
        }

        @Nullable
        @Override
        public String getClassName() {
            return className;
        }

        @Override
        public boolean isComposite() {
            return composite;
        }
    }
}
