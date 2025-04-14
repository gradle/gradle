package com.example;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.testing.GroupTestEventReporter;
import org.gradle.api.tasks.testing.TestEventReporter;
import org.gradle.api.tasks.testing.TestEventReporterFactory;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.junit.platform.launcher.listeners.TestExecutionSummary;

import javax.inject.Inject;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

/**
 * A custom task that demonstrates the {@code TestEventReporter} API.
 */
public abstract class CustomTest extends DefaultTask {

    @Inject
    public abstract ProjectLayout getLayout();

    @Inject
    protected abstract TestEventReporterFactory getTestEventReporterFactory();


    @TaskAction
    void runTests() {
        try (GroupTestEventReporter root = getTestEventReporterFactory().createTestEventReporter(
            "root",
            getLayout().getBuildDirectory().dir("test-results/custom-test").get(),
            getLayout().getBuildDirectory().dir("reports/tests/custom-test").get()
        )) {
            root.started(Instant.now());

            List<String> failedTests = new ArrayList<>();

            try (GroupTestEventReporter junittest = root.reportTestGroup("CustomJUnitTestSuite")) {
                junittest.started(Instant.now());

                LauncherDiscoveryRequest request = LauncherDiscoveryRequestBuilder.request()
                    .selectors(
                        selectClass(MyTest1.class)
                    )
                    .build();

                Launcher launcher = LauncherFactory.create();
                TestExecutionSummary summary = executeTests(launcher, request);

                summary.getFailures().forEach(result -> {
                    try (TestEventReporter test = junittest.reportTest(result.getTestIdentifier().getDisplayName(),
                        result.getTestIdentifier().getLegacyReportingName())) {
                        test.started(Instant.now());
                        String testName = String.valueOf(result.getTestIdentifier().getParentIdObject());
                        failedTests.add(testName);
                        test.metadata(Instant.now(), "Parent class:", String.valueOf(result.getTestIdentifier().getParentId().get()));
                        test.failed(Instant.now(), String.valueOf(result.getException()));
                    }
                });

                String failedTestsList = String.join(", ", failedTests);
                junittest.metadata(Instant.now(), "Tests that failed:", failedTestsList);

                if (summary.getTestsFailedCount() > 0) {
                    junittest.failed(Instant.now());
                } else {
                    junittest.succeeded(Instant.now());
                }
            }

            if (!failedTests.isEmpty()) {
                root.failed(Instant.now());
            } else {
                root.succeeded(Instant.now());
            }
        }
    }

    private TestExecutionSummary executeTests(Launcher launcher, LauncherDiscoveryRequest request) {
        // Use SummaryGeneratingListener to collect test execution data
        SummaryGeneratingListener listener = new SummaryGeneratingListener();
        launcher.registerTestExecutionListeners(listener);

        // Execute the tests
        launcher.execute(request);

        // Return the summary generated by the listener
        return listener.getSummary();
    }
}

