package com.example;

import org.gradle.api.DefaultTask;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.options.Option;
import org.gradle.api.tasks.testing.GroupTestEventReporter;
import org.gradle.api.tasks.testing.TestEventReporter;
import org.gradle.api.tasks.testing.TestEventReporterFactory;
import org.gradle.api.tasks.testing.TestOutputEvent;

import javax.inject.Inject;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.Random;

/**
 * A custom task that demonstrates the {@code TestEventReporter} API.
 */
public abstract class CustomTest extends DefaultTask {
    /**
     * CLI option to always generate a test failure when demonstrating the {@code TestEventReporter} API.
     *
     * @return property with value {@code true} if the task should demonstrate a test failure, {@code false} otherwise
     */
    @Input
    @Option(option="fail", description = "Tells the task to demonstrate failures.")
    public abstract Property<Boolean> getFail();

    @Inject
    protected abstract TestEventReporterFactory getTestEventReporterFactory();

    @TaskAction
    void runTests() {
        // This task is a demonstration of generating the proper test events.
        // It simulates a variety of conditions and nesting levels

        // The API uses try-with-resources and AutoCloseable to enforce lifecycle checks
        // You can manually call close() on a reporter. Once closed or completed, a test/group cannot generate
        // more events
        try (GroupTestEventReporter root = getTestEventReporterFactory().createTestEventReporter(getName())) {
            root.started(Instant.now());

            // Demonstrate a test suite with multiple test outcomes
            // This has one level of nesting similar to JUnit
            try (GroupTestEventReporter suite = root.reportTestGroup("MyTestSuite")) {
                // Start a group of test cases
                suite.started(Instant.now());

                // Simulate 10 tests running
                for (int i=0; i<10; i++) {

                    try (TestEventReporter test = suite.reportTest("test" + i, "test(" + i + ")")) {
                        // Start an individual test case
                        test.started(Instant.now());

                        // Output must occur between started and terminal methods (succeeded, failed, skipped)
                        test.output(Instant.now(), TestOutputEvent.Destination.StdOut, "This is some standard output");
                        test.output(Instant.now(), TestOutputEvent.Destination.StdErr, "This is some standard error");

                        // Every 3 tests are considered skipped
                        if (i % 3 == 0) {
                            test.skipped(Instant.now());
                        } else {
                            test.succeeded(Instant.now());
                        }
                    }
                }
                // the suite needs to be completed after all tests
                suite.succeeded(Instant.now());
            }

            // Demonstrate arbitrary nesting levels
            try (GroupTestEventReporter outer = root.reportTestGroup("OuterNestingSuite")) {
                outer.started(Instant.now());
                // The parent-child relationship is expressed by creating the child with the parent's reportXXX method
                try (GroupTestEventReporter deeper = outer.reportTestGroup("DeeperNestingSuite")) {
                    deeper.started(Instant.now());
                    try (GroupTestEventReporter inner = deeper.reportTestGroup("InnerNestingSuite")) {
                        inner.started(Instant.now());
                        try (TestEventReporter test = inner.reportTest("nestedTest", "nestedTest()")) {
                            // This test is found at OuterNestingSuite > DeeperNestingSuite > InnerNestingSuite > nestedTest()
                            test.started(Instant.now());
                            test.succeeded(Instant.now());
                        }
                        inner.succeeded(Instant.now());
                    }
                    deeper.succeeded(Instant.now());
                }
                outer.succeeded(Instant.now());
            }

            // If requested, demonstrate a failing test
            if (getFail().get()) {
                try (GroupTestEventReporter suite = root.reportTestGroup("FailingSuite")) {
                    suite.started(Instant.now());
                    try (TestEventReporter test = suite.reportTest("failingTest", "failingTest()")) {
                        test.started(Instant.now());
                        test.failed(Instant.now(), "This is a test failure");
                    }
                    suite.failed(Instant.now(), "This is additional message for the suite failure");
                }
                root.failed(Instant.now());
            } else {
                root.succeeded(Instant.now());
            }
        }
    }
}
