/*
 * Copyright 2012 the original author or authors.
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

import org.gradle.internal.UncheckedException;
import org.junit.runner.Description;
import org.junit.runner.RunWith;
import org.junit.runner.Runner;
import org.junit.runner.manipulation.Filter;
import org.junit.runner.manipulation.Filterable;
import org.junit.runner.manipulation.NoTestsRemainException;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunListener;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.Suite;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.RunnerBuilder;
import org.spockframework.runtime.Sputnik;
import org.spockframework.runtime.model.FeatureInfo;
import org.spockframework.runtime.model.IterationInfo;
import org.spockframework.runtime.model.NameProvider;
import org.spockframework.runtime.model.SpecInfo;

import javax.annotation.Nullable;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A base class for those test runners which execute a test multiple times.
 */
public abstract class AbstractMultiTestRunner extends Runner implements Filterable {
    protected final Class<?> target;
    private final List<Execution> executions = new ArrayList<Execution>();
    private final boolean runAllExecutions;
    private Description description;
    private Description templateDescription;
    private boolean executionsInitialized;

    protected AbstractMultiTestRunner(Class<?> target) {
        this(target, true);
    }

    protected AbstractMultiTestRunner(Class<?> target, boolean runAllExecutions) {
        this.target = target;
        this.runAllExecutions = runAllExecutions;
    }

    @Override
    public Description getDescription() {
        initDescription();
        return description;
    }

    @Override
    public void run(RunNotifier notifier) {
        initDescription();

        for (Execution execution : executions) {
            execution.run(notifier);
            if (!runAllExecutions && execution.hasAnyEnabledTests()) {
                break;
            }
        }
    }

    @Override
    public void filter(Filter filter) throws NoTestsRemainException {
        initExecutions();
        for (Execution execution : executions) {
            execution.filter(filter);
        }
        invalidateDescription();
    }

    private void initExecutions() {
        if (!executionsInitialized) {
            try {
                UnrollAwareSuite descriptionProvider = createRunnerFor(Collections.singletonList(target), Collections.<Filter>emptyList(), null);
                templateDescription = descriptionProvider.getDescription();
            } catch (InitializationError initializationError) {
                throw UncheckedException.throwAsUncheckedException(initializationError);
            }
            createExecutions();
            for (Execution execution : executions) {
                execution.init(target, templateDescription);
            }
            executionsInitialized = true;
        }
    }

    private void initDescription() {
        initExecutions();
        if (description == null) {
            description = Description.createSuiteDescription(target);
            for (Execution execution : executions) {
                execution.addDescriptions(description);
            }
        }
    }

    private void invalidateDescription() {
        description = null;
        templateDescription = null;
    }

    protected abstract void createExecutions();

    protected void add(Execution execution) {
        executions.add(execution);
    }

    private static UnrollAwareSuite createRunnerFor(List<? extends Class<?>> targetClasses, final List<Filter> filters, Execution execution) throws InitializationError {
        RunnerBuilder runnerBuilder = new RunnerBuilder() {
            @Override
            public Runner runnerForClass(Class<?> testClass) throws Throwable {
                for (Class<?> candidate = testClass; candidate != null; candidate = candidate.getSuperclass()) {
                    RunWith runWith = candidate.getAnnotation(RunWith.class);
                    if (runWith != null && !AbstractMultiTestRunner.class.isAssignableFrom(runWith.value())) {
                        try {
                            Runner r = (Runner) runWith.value().getConstructors()[0].newInstance(testClass);
                            return filter(r);
                        } catch (InvocationTargetException e) {
                            throw e.getTargetException();
                        }
                    }
                }
                return filter(new BlockJUnit4ClassRunner(testClass));
            }

            //we need to filter at the level child runners because the suite is not doing the right thing here
            private Runner filter(Runner r) {
                for (Filter filter : filters) {
                    try {
                        ((Filterable) r).filter(filter);
                    } catch (NoTestsRemainException e) {
                        //ignore
                    }
                }
                return r;
            }
        };
        return new UnrollAwareSuite(runnerBuilder, targetClasses.toArray(new Class<?>[0]), execution);
    }

    private static class UnrollAwareSuite extends Suite {

        private static final Method SPEC_METHOD;

        static {
            Method spec = null;
            try {
                spec = Sputnik.class.getDeclaredMethod("getSpec");
                spec.setAccessible(true);
            } catch (NoSuchMethodException e) {
                spec = null;
            } finally {
                SPEC_METHOD = spec;
            }
        }

        public UnrollAwareSuite(RunnerBuilder builder, Class<?>[] classes, final Execution execution) throws InitializationError {
            super(builder, classes);
            if (execution != null) {
                fixupNameProvider(execution);
            }
        }

        private void fixupNameProvider(final Execution execution) {
            for (Runner child : getChildren()) {
                if (child instanceof Sputnik && SPEC_METHOD != null) {
                    try {
                        Description childDescription = child.getDescription();
                        SpecInfo spec = (SpecInfo) SPEC_METHOD.invoke(child);
                        List<FeatureInfo> allFeatures = spec.getAllFeatures();
                        for (FeatureInfo feature : allFeatures) {
                            feature.setSkipped(feature.isSkipped() || !execution.isTestEnabled(new TestDescriptionBackedTestDetails(childDescription, feature.getDescription())));
                            final NameProvider<IterationInfo> provider = feature.getIterationNameProvider();
                            if (provider!=null) {
                                feature.setIterationNameProvider(new NameProvider<IterationInfo>() {
                                    @Override
                                    public String getName(IterationInfo iterationInfo) {
                                        return provider.getName(iterationInfo) + " [" + execution.getDisplayName() + "]";
                                    }
                                });
                            }
                        }
                    } catch (IllegalAccessException e) {
                        // no luck
                    } catch (InvocationTargetException e) {
                        // no luck
                    }
                }
            }
        }
    }

    protected static abstract class Execution implements Filterable {
        protected Class<?> target;
        private Description templateDescription;
        private final Map<Description, Description> descriptionTranslations = new HashMap<Description, Description>();
        private final Set<Description> enabledTests = new LinkedHashSet<Description>();
        private final Set<Description> disabledTests = new LinkedHashSet<Description>();
        private final List<Filter> filters = new LinkedList<Filter>();

        final boolean hasAnyEnabledTests() {
            return !enabledTests.isEmpty();
        }

        final void init(Class<?> target, Description templateDescription) {
            this.target = target;
            this.templateDescription = templateDescription;
        }

        private Runner createExecutionRunner() throws InitializationError {
            List<? extends Class<?>> targetClasses = loadTargetClasses();
            return createRunnerFor(targetClasses, filters, this);
        }

        final void addDescriptions(Description parent) {
            map(templateDescription, parent);
        }

        final void run(final RunNotifier notifier) {
            RunNotifier nested = new RunNotifier();
            NestedRunListener nestedListener = new NestedRunListener(notifier);
            nested.addListener(nestedListener);

            try {
                runEnabledTests(nested);
            } finally {
                nestedListener.cleanup();
            }

            for (Description disabledTest : disabledTests) {
                nested.fireTestIgnored(disabledTest);
            }
        }

        private void runEnabledTests(RunNotifier nested) {
            if (enabledTests.isEmpty()) {
                return;
            }

            Runner runner;
            try {
                runner = createExecutionRunner();
            } catch (Throwable t) {
                runner = new CannotExecuteRunner(getDisplayName(), target, t);
            }

            try {
                if (!disabledTests.isEmpty()) {
                    ((Filterable) runner).filter(new Filter() {
                        @Override
                        public boolean shouldRun(Description description) {
                            return !disabledTests.contains(description);
                        }

                        @Override
                        public String describe() {
                            return "disabled tests";
                        }
                    });
                }
            } catch (NoTestsRemainException e) {
                return;
            }

            runner.run(nested);
        }

        private Description translateDescription(Description description) {
            return descriptionTranslations.containsKey(description) ? descriptionTranslations.get(description) : description;
        }

        @Override
        public void filter(Filter filter) throws NoTestsRemainException {
            filters.add(filter);
            for (Map.Entry<Description, Description> entry : descriptionTranslations.entrySet()) {
                if (!filter.shouldRun(entry.getKey())) {
                    enabledTests.remove(entry.getValue());
                    disabledTests.remove(entry.getValue());
                }
            }
        }

        protected void before() {
        }

        protected void after() {
        }

        private void map(Description source, Description parent) {
            for (Description child : source.getChildren()) {
                Description mappedChild;
                if (child.getMethodName() != null) {
                    mappedChild = Description.createSuiteDescription(String.format("%s [%s](%s)", child.getMethodName(), getDisplayName(), child.getClassName()));
                    parent.addChild(mappedChild);
                    if (!isTestEnabled(new TestDescriptionBackedTestDetails(source, child))) {
                        disabledTests.add(child);
                    } else {
                        enabledTests.add(child);
                    }
                } else {
                    mappedChild = Description.createSuiteDescription(child.getClassName());
                }
                descriptionTranslations.put(child, mappedChild);
                map(child, parent);
            }
        }

        /**
         * Returns a display name for this execution. Used in the JUnit descriptions for test execution.
         */
        protected abstract String getDisplayName();

        /**
         * Returns true if the given test should be executed, false if it should be ignored. Default is true.
         */
        protected boolean isTestEnabled(TestDetails testDetails) {
            return true;
        }

        /**
         * Checks that this execution can be executed, throwing an exception if not.
         */
        protected void assertCanExecute() {
        }

        /**
         * Loads the target classes for this execution. Default is the target class that this runner was constructed with.
         */
        protected List<? extends Class<?>> loadTargetClasses() {
            return Collections.singletonList(target);
        }

        private static class CannotExecuteRunner extends Runner {
            private final Description description;
            private final Throwable failure;

            public CannotExecuteRunner(String displayName, Class<?> testClass, Throwable failure) {
                description = Description.createSuiteDescription(String.format("%s(%s)", displayName, testClass.getName()));
                this.failure = failure;
            }

            @Override
            public Description getDescription() {
                return description;
            }

            @Override
            public void run(RunNotifier notifier) {
                Description description = getDescription();
                notifier.fireTestStarted(description);
                notifier.fireTestFailure(new Failure(description, failure));
                notifier.fireTestFinished(description);
            }
        }

        private class NestedRunListener extends RunListener {
            private final RunNotifier notifier;
            boolean started;
            boolean complete;

            public NestedRunListener(RunNotifier notifier) {
                this.notifier = notifier;
            }

            @Override
            public void testStarted(Description description) {
                Description translated = translateDescription(description);
                notifier.fireTestStarted(translated);
                if (!started && !complete) {
                    try {
                        assertCanExecute();
                        started = true;
                        before();
                    } catch (Throwable t) {
                        notifier.fireTestFailure(new Failure(translated, t));
                    }
                }
            }

            @Override
            public void testFailure(Failure failure) {
                Description translated = translateDescription(failure.getDescription());
                notifier.fireTestFailure(new Failure(translated, failure.getException()));
            }

            @Override
            public void testAssumptionFailure(Failure failure) {
                Description translated = translateDescription(failure.getDescription());
                notifier.fireTestAssumptionFailed(new Failure(translated, failure.getException()));
            }

            @Override
            public void testIgnored(Description description) {
                Description translated = translateDescription(description);
                notifier.fireTestIgnored(translated);
            }

            @Override
            public void testFinished(Description description) {
                Description translated = translateDescription(description);
                notifier.fireTestFinished(translated);
            }

            public void cleanup() {
                if (started) {
                    after();
                }
                // Prevent further tests (ignored) from triggering start actions
                complete = true;
            }
        }
    }

    public interface TestDetails {
        /**
         * Locates the given annotation for the test. May be inherited from test class.
         */
        @Nullable
        <A extends Annotation> A getAnnotation(Class<A> type);
    }

    private static class TestDescriptionBackedTestDetails implements TestDetails {
        private final Description parent;
        private final Description test;

        private TestDescriptionBackedTestDetails(Description parent, Description test) {
            this.parent = parent;
            this.test = test;
        }

        @Override
        public String toString() {
            return test.toString();
        }

        @Override
        public <A extends Annotation> A getAnnotation(Class<A> type) {
            A annotation = test.getAnnotation(type);
            if (annotation != null) {
                return annotation;
            }
            return parent.getAnnotation(type);
        }
    }
}
