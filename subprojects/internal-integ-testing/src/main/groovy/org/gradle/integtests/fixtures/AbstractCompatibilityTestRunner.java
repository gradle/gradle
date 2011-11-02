/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.integtests.fixtures;

import org.junit.internal.runners.ErrorReportingRunner;
import org.junit.runner.Description;
import org.junit.runner.Runner;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunListener;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.Suite;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.RunnerBuilder;
import org.junit.runners.model.Statement;

import java.util.*;

/**
 * A base class for those test runners which execute a test multiple times against a set of Gradle versions.
 */
abstract class AbstractCompatibilityTestRunner extends Runner {
    private final Class<?> target;
    private Description description;
    private List<Execution> executions;
    final GradleDistribution current = new GradleDistribution();
    final List<BasicGradleDistribution> previous;

    AbstractCompatibilityTestRunner(Class<?> target) {
        this.target = target;
        previous = new ArrayList<BasicGradleDistribution>();
        for (String version : Arrays.asList(
                "0.8",
                "0.9-rc-3",
                "0.9",
                "0.9.1",
                "0.9.2",
                "1.0-milestone-1",
                "1.0-milestone-2",
                "1.0-milestone-3",
                "1.0-milestone-4",
                "1.0-milestone-5")) {
            previous.add(current.previousVersion(version));
        }
    }

    public List<BasicGradleDistribution> getPrevious() {
        return previous;
    }

    @Override
    public Description getDescription() {
        init();
        return description;
    }

    @Override
    public void run(RunNotifier notifier) {
        init();
        for (Execution execution : executions) {
            execution.run(notifier);
        }
    }

    private void init() {
        if (description == null) {
            executions = createExecutions();
            description = Description.createSuiteDescription(target);
            for (Execution execution : executions) {
                execution.init(target);
                execution.addDescriptions(description);
            }
        }
    }

    protected abstract List<Execution> createExecutions();

    protected static abstract class Execution {
        private Runner runner;
        private Class<?> target;
        private final Map<Description, Description> descriptionTranslations = new HashMap<Description, Description>();

        final void init(Class<?> target) {
            this.target = target;
            if (isEnabled()) {
                List<? extends Class<?>> targetClasses = loadTargetClasses();
                RunnerBuilder runnerBuilder = new RunnerBuilder() {
                    @Override
                    public Runner runnerForClass(Class<?> testClass) {
                        try {
                            return new BlockJUnit4ClassRunner(testClass) {
                                @Override
                                protected Statement methodInvoker(FrameworkMethod method, Object test) {
                                    Statement statement = super.methodInvoker(method, test);
                                    return Execution.this.methodInvoker(statement, method, test);
                                }
                            };
                        } catch (InitializationError initializationError) {
                            return new ErrorReportingRunner(testClass, initializationError);
                        }
                    }
                };
                try {
                    runner = new Suite(runnerBuilder, targetClasses.toArray(new Class<?>[targetClasses.size()]));
                } catch (InitializationError initializationError) {
                    runner = new ErrorReportingRunner(target, initializationError);
                }
            }
        }

        final void addDescriptions(Description parent) {
            if (runner != null) {
                map(runner.getDescription(), parent);
            }
        }

        final void run(final RunNotifier notifier) {
            if (runner == null) {
                Description description = Description.createSuiteDescription("ignored [${displayName}](${target.name})");
                notifier.fireTestIgnored(description);
                return;
            }

            RunNotifier nested = new RunNotifier();
            nested.addListener(new RunListener() {
                @Override
                public void testStarted(Description description) {
                    Description translated = descriptionTranslations.get(description);
                    notifier.fireTestStarted(translated);
                }

                @Override
                public void testFailure(Failure failure) {
                    Description translated = descriptionTranslations.get(failure.getDescription());
                    notifier.fireTestFailure(new Failure(translated, failure.getException()));
                }

                @Override
                public void testAssumptionFailure(Failure failure) {
                    Description translated = descriptionTranslations.get(failure.getDescription());
                    notifier.fireTestAssumptionFailed(new Failure(translated, failure.getException()));
                }

                @Override
                public void testIgnored(Description description) {
                    Description translated = descriptionTranslations.get(description);
                    notifier.fireTestIgnored(translated);
                }

                @Override
                public void testFinished(Description description) {
                    Description translated = descriptionTranslations.get(description);
                    notifier.fireTestFinished(translated);
                }
            });

            runner.run(nested);
        }

        private void map(Description source, Description parent) {
            for (Description child : source.getChildren()) {
                Description mappedChild;
                if (child.getMethodName()!= null) {
                    mappedChild = Description.createSuiteDescription(String.format("%s [%s](%s)", child.getMethodName(), getDisplayName(), child.getClassName()));
                    parent.addChild(mappedChild);
                } else {
                    mappedChild = Description.createSuiteDescription(child.getClassName());
                }
                descriptionTranslations.put(child, mappedChild);
                map(child, parent);
            }
        }

        /**
         * Returns a display name for this execution. Used in the Junit descriptions for test execution.
         */
        abstract String getDisplayName();

        /**
         * Can modify the execution of a given test. The returned statement is executed after the rules and befores of the test have been executed.
         */
        protected Statement methodInvoker(Statement statement, FrameworkMethod method, Object test) {
            return statement;
        }

        /**
         * Returns true if this execution should be executed, false if it should be ignored. Default is true.
         */
        boolean isEnabled() {
            return true;
        }

        /**
         * Loads the target classes for this execution. Default is the target class that this runner was constructed with.
         */
        List<? extends Class<?>> loadTargetClasses() {
            return Arrays.asList(target);
        }
    }
}
