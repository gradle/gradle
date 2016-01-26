package org.gradle;

import org.junit.Ignore;
import org.junit.runner.Description;
import org.junit.runner.RunWith;
import org.junit.runner.Runner;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunNotifier;

import java.util.ArrayList;
import java.util.List;

@Ignore
@RunWith(org.gradle.CustomIgnoredTest.TheRunner.class)
public class CustomIgnoredTest {
    static int count = 0;

    public boolean doSomething() {
        return true;
    }

    public static class TheRunner extends Runner {
        List descriptions = new ArrayList();
        private final Class<? extends org.gradle.CustomIgnoredTest> testClass;
        private final org.gradle.CustomIgnoredTest testContainingInstance;
        private Description testSuiteDescription;

        public TheRunner(Class<? extends org.gradle.CustomIgnoredTest> testClass) {
            this.testClass = testClass;
            testContainingInstance = reflectMeATestContainingInstance(testClass);
            testSuiteDescription = Description.createSuiteDescription("Custom Test with Suite ");
            testSuiteDescription.addChild(createTestDescription("first test run"));
            testSuiteDescription.addChild(createTestDescription("second test run"));
            testSuiteDescription.addChild(createTestDescription("third test run"));
        }

        @Override
        public Description getDescription() {
            return testSuiteDescription;
        }

        @Override
        public void run(RunNotifier notifier) {
            for (Description description : testSuiteDescription.getChildren()) {
                notifier.fireTestStarted(description);
                try {
                    if (testContainingInstance.doSomething()) {
                        notifier.fireTestFinished(description);
                    } else {
                        notifier.fireTestIgnored(description);
                    }
                } catch (Exception e) {
                    notifier.fireTestFailure(new Failure(description, e));
                }
            }
        }

        private org.gradle.CustomIgnoredTest reflectMeATestContainingInstance(Class<? extends org.gradle.CustomIgnoredTest> testClass) {
            try {
                return testClass.newInstance();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        private Description createTestDescription(String description) {
            return Description.createTestDescription(testClass, description);
        }
    }
}