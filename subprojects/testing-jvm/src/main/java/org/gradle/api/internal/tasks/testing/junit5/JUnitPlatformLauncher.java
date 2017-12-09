package org.gradle.api.internal.tasks.testing.junit5;

import org.gradle.api.internal.tasks.testing.TestCompleteEvent;
import org.gradle.api.internal.tasks.testing.TestDescriptorInternal;
import org.gradle.api.internal.tasks.testing.TestResultProcessor;
import org.gradle.api.internal.tasks.testing.TestStartEvent;
import org.gradle.api.tasks.testing.TestOutputEvent;
import org.gradle.api.tasks.testing.TestResult;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.reporting.ReportEntry;
import org.junit.platform.launcher.*;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;

import javax.annotation.Nullable;

import java.util.function.Consumer;

import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

public class JUnitPlatformLauncher {
    private final Launcher launcher;

    public JUnitPlatformLauncher(TestResultProcessor resultProcessor) {
        this.launcher = LauncherFactory.create();
        launcher.registerTestExecutionListeners(new GradleTestExecutionListener(resultProcessor));
    }

    public void execute(String testClassName) {
        LauncherDiscoveryRequest request = LauncherDiscoveryRequestBuilder.request()
            .selectors(selectClass(testClassName))
            .build();

        launcher.execute(request);
    }

    private static class GradleTestExecutionListener implements TestExecutionListener {
        private final TestResultProcessor resultProcessor;

        public GradleTestExecutionListener(TestResultProcessor resultProcessor) {
            this.resultProcessor = resultProcessor;
        }

        @Override
        public void testPlanExecutionStarted(TestPlan testPlan) {
            // TODO ignore for now
        }

        @Override
        public void testPlanExecutionFinished(TestPlan testPlan) {
            // TODO ignore for now
        }

        @Override
        public void dynamicTestRegistered(TestIdentifier testIdentifier) {
            // TODO ignore for now
        }

        @Override
        public void executionSkipped(TestIdentifier testIdentifier, String reason) {
            resultProcessor.completed(testIdentifier, new TestCompleteEvent(System.currentTimeMillis(), TestResult.ResultType.SKIPPED));
        }

        @Override
        public void executionStarted(TestIdentifier testIdentifier) {
            resultProcessor.started(new GradleTestDescriptor(testIdentifier), new TestStartEvent(System.currentTimeMillis()));
        }

        @Override
        public void executionFinished(final TestIdentifier testIdentifier, TestExecutionResult testExecutionResult) {
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
                    resultProcessor.failure(testIdentifier, throwable);
                }
            });

            resultProcessor.completed(testIdentifier, new TestCompleteEvent(System.currentTimeMillis(), result));
        }

        @Override
        public void reportingEntryPublished(TestIdentifier testIdentifier, ReportEntry entry) {
            // TODO ignore for now
        }
    }

    private static class GradleTestDescriptor implements TestDescriptorInternal {
        private final TestIdentifier id;

        public GradleTestDescriptor(TestIdentifier id) {
            this.id = id;
        }

        @Override
        public String getName() {
            return id.getLegacyReportingName();
        }

        @Nullable
        @Override
        public String getClassName() {
            return null;
        }

        @Override
        public boolean isComposite() {
            return id.isContainer();
        }

        @Nullable
        @Override
        public TestDescriptorInternal getParent() {

        }

        @Override
        public Object getId() {
            return id.getUniqueId();
        }

        @Nullable
        @Override
        public Object getOwnerBuildOperationId() {
            return null;
        }
    }
}
