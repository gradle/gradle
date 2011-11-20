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

import org.gradle.os.OperatingSystem;
import org.gradle.util.Jvm;
import org.junit.internal.runners.ErrorReportingRunner;
import org.junit.runner.Description;
import org.junit.runner.RunWith;
import org.junit.runner.Runner;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunListener;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.Suite;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.RunnerBuilder;

import java.util.*;

/**
 * A base class for those test runners which execute a test multiple times against a set of Gradle versions.
 */
public abstract class AbstractCompatibilityTestRunner extends Runner {
    protected final Class<?> target;
    private Description description;
    private final List<Execution> executions = new ArrayList<Execution>();
    protected final GradleDistribution current = new GradleDistribution();
    protected final List<BasicGradleDistribution> previous;

    protected AbstractCompatibilityTestRunner(Class<?> target) {
        this.target = target;
        previous = new ArrayList<BasicGradleDistribution>();
        String versionStr = System.getProperty("org.gradle.integtest.versions", "latest");
        List<String> versions;
        versions = Arrays.asList(
                "0.8",
                "0.9-rc-3",
                "0.9",
                "0.9.1",
                "0.9.2",
                "1.0-milestone-1",
                "1.0-milestone-2",
                "1.0-milestone-3",
                "1.0-milestone-4",
                "1.0-milestone-5",
                "1.0-milestone-6");
        if (!versionStr.equals("all")) {
            versions = Collections.singletonList(versions.get(versions.size() - 1));
        }
        for (String version : versions) {
            BasicGradleDistribution previous = current.previousVersion(version);
            if (!previous.worksWith(Jvm.current())) {
                executions.add(new IgnoredVersion(previous, "does not work with current JVM"));
                continue;
            }
            if (!previous.worksWith(OperatingSystem.current())) {
                executions.add(new IgnoredVersion(previous, "does not work with current OS"));
                continue;
            }
            this.previous.add(previous);
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
            executions.addAll(createExecutions());
            description = Description.createSuiteDescription(target);
            for (Execution execution : executions) {
                execution.init(target);
                execution.addDescriptions(description);
            }
        }
    }

    protected abstract List<? extends Execution> createExecutions();

    protected static abstract class Execution {
        private Runner runner;
        protected Class<?> target;
        private final Map<Description, Description> descriptionTranslations = new HashMap<Description, Description>();

        final void init(Class<?> target) {
            this.target = target;
            if (isEnabled()) {
                List<? extends Class<?>> targetClasses = loadTargetClasses();
                RunnerBuilder runnerBuilder = new RunnerBuilder() {
                    @Override
                    public Runner runnerForClass(Class<?> testClass) {
                        try {
                            for (Class<?> candidate = testClass; candidate != null; candidate = candidate.getSuperclass()) {
                                RunWith runWith = candidate.getAnnotation(RunWith.class);
                                if (runWith != null && !AbstractCompatibilityTestRunner.class.isAssignableFrom(runWith.value())) {
                                    try {
                                        return (Runner)runWith.value().getConstructors()[0].newInstance(testClass);
                                    } catch (Exception e) {
                                        return new ErrorReportingRunner(testClass, e);
                                    }
                                }
                            }
                            return new BlockJUnit4ClassRunner(testClass);
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
                Description description = Description.createSuiteDescription(String.format("%s(%s)", getDisplayName(), target.getName()));
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

            before();
            try {
                runner.run(nested);
            } finally {
                after();
            }
        }

        protected void before() {
        }

        protected void after() {
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
        protected abstract String getDisplayName();

        /**
         * Returns true if this execution should be executed, false if it should be ignored. Default is true.
         */
        protected boolean isEnabled() {
            return true;
        }

        /**
         * Loads the target classes for this execution. Default is the target class that this runner was constructed with.
         */
        protected List<? extends Class<?>> loadTargetClasses() {
            return Arrays.asList(target);
        }
    }

    private static class IgnoredVersion extends Execution {
        private final BasicGradleDistribution distribution;
        private final String why;

        private IgnoredVersion(BasicGradleDistribution distribution, String why) {
            this.distribution = distribution;
            this.why = why;
        }

        @Override
        protected boolean isEnabled() {
            return false;
        }

        @Override
        protected String getDisplayName() {
            return String.format("%s %s", distribution.getVersion(), why);
        }
    }
}
