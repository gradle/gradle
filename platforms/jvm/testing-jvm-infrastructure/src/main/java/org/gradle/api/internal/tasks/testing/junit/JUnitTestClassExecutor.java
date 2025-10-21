/*
 * Copyright 2010 the original author or authors.
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

package org.gradle.api.internal.tasks.testing.junit;

import org.gradle.api.GradleException;
import org.gradle.api.internal.tasks.testing.TestClassConsumer;
import org.gradle.api.internal.tasks.testing.TestResultProcessor;
import org.gradle.api.internal.tasks.testing.filter.TestFilterSpec;
import org.gradle.api.internal.tasks.testing.filter.TestSelectionMatcher;
import org.gradle.api.tasks.testing.TestFailure;
import org.gradle.internal.concurrent.ThreadSafe;
import org.gradle.internal.id.IdGenerator;
import org.gradle.internal.time.Clock;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.Description;
import org.junit.runner.JUnitCore;
import org.junit.runner.Request;
import org.junit.runner.RunWith;
import org.junit.runner.Runner;
import org.junit.runner.manipulation.Filter;
import org.junit.runner.manipulation.Filterable;
import org.junit.runner.manipulation.NoTestsRemainException;
import org.junit.runner.notification.RunListener;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class JUnitTestClassExecutor implements TestClassConsumer {
    private final ClassLoader applicationClassLoader;
    private final JUnitSpec spec;
    private final TestClassExecutionListener executionListener;
    private final RunListener listener;
    private @Nullable CategoryFilter categoryFilter;

    public JUnitTestClassExecutor(
        ClassLoader applicationClassLoader,
        JUnitSpec spec,
        Clock clock,
        IdGenerator<?> idGenerator,
        TestClassExecutionListener executionListener,
        TestResultProcessor threadSafeResultProcessor
    ) {
        assert executionListener instanceof ThreadSafe;
        this.applicationClassLoader = applicationClassLoader;
        this.spec = spec;
        this.executionListener = executionListener;
        this.listener = new JUnitTestEventAdapter(threadSafeResultProcessor, clock, idGenerator);

        if (spec.hasCategoryConfiguration()) {
            verifyJUnitCategorySupport(applicationClassLoader);
            categoryFilter = new CategoryFilter(spec.getIncludeCategories(), spec.getExcludeCategories(), applicationClassLoader);
            categoryFilter.verifyCategories(applicationClassLoader);
        }
    }

    @Override
    public void consumeClass(String testClassName) {
        boolean started = false;
        try {
            Request request = shouldRunTestClass(testClassName);
            if (request == null) {
                return;
            }

            executionListener.testClassStarted(testClassName);
            started = true;
            runRequest(request);
            started = false;
            executionListener.testClassFinished(null);
        } catch (Throwable throwable) {
            if (started) {
                executionListener.testClassFinished(TestFailure.fromTestFrameworkFailure(throwable));
            } else {
                // If we haven't even started to run the request, this is a Gradle problem, so propagate it
                throw new GradleException("Failed to execute test class: '" + testClassName + "'.", throwable);
            }

            // Don't ever swallow Errors, as they likely indicate JVM problems that should always propagate
            if (throwable instanceof Error) {
                throw (Error) throwable;
            }
        }
    }

    @Nullable
    private Request shouldRunTestClass(String testClassName) throws ClassNotFoundException {
        final Class<?> testClass = Class.forName(testClassName, false, applicationClassLoader);
        if (isNestedClassInsideEnclosedRunner(testClass)) {
            return null;
        }

        // See if there is anything left to run after applying filters, as we could filter
        // out every method on this class, or even the entire class itself.
        Request filteredRequest = buildFilteredRequest(testClass);
        if (filteredRequest == null) {
            return null;
        }

        return filteredRequest;
    }

    /**
     * Builds a new {@link Request} for the given test class, applying any filters present in the {@link #spec}.
     * <p>
     * Note that we can't use {@link Request#runner(Runner)} for this, as it didn't exist in JUnit 4.0. But since there's
     * only one abstract method to implement on {@link Runner}, we can easily build an implementation.
     *
     * @param testClass the test class we're requesting to run
     * @return the filtered request ready to be run, or {@code null} if no tests should be run according to the filters
     */
    @Nullable
    private Request buildFilteredRequest(Class<?> testClass) {
        Request originalRequest = Request.aClass(testClass);
        Runner runner = originalRequest.getRunner();

        List<Filter> filters = buildFilters(testClass.getName(), runner);
        if (runner instanceof Filterable) {
            Filterable filterable = (Filterable) runner;
            for (Filter filter : filters) {
                try {
                    filterable.filter(filter);
                } catch (NoTestsRemainException e) {
                    // Ignore
                    return null;
                }
            }
        } else if (allTestsFiltered(runner, filters)) {
            return null;
        }

        return new FilteredGradleRequest(runner);
    }

    @Nullable
    private List<Filter> buildFilters(String testClassName, Runner filteredRunner) {
        List<Filter> filters = new ArrayList<>();
        if (categoryFilter != null) {
            filters.add(categoryFilter);
        }

        TestFilterSpec filterSpec = spec.getFilter();
        if (!filterSpec.getIncludedTests().isEmpty()
            || !filterSpec.getIncludedTestsCommandLine().isEmpty()
            || !filterSpec.getExcludedTests().isEmpty()) {
            TestSelectionMatcher matcher = new TestSelectionMatcher(filterSpec);
            // For test suites (including suite-like custom Runners), if the test suite class
            // matches the filter, run the entire suite instead of filtering away its contents.
            if (!filteredRunner.getDescription().isSuite() || !matcher.matchesTest(testClassName, null)) {
                filters.add(new MethodNameFilter(matcher));
            }
        }

        return filters;
    }

    private void runRequest(Request request) {
        JUnitCore junit = new JUnitCore();
        junit.addListener(listener);
        junit.run(request);
    }

    // https://github.com/gradle/gradle/issues/2319
    public static boolean isNestedClassInsideEnclosedRunner(Class<?> testClass) {
        if (testClass.getEnclosingClass() == null) {
            return false;
        }

        Class<?> outermostClass = testClass;
        while (outermostClass.getEnclosingClass() != null) {
            outermostClass = outermostClass.getEnclosingClass();
        }

        RunWith runWith = outermostClass.getAnnotation(RunWith.class);
        return runWith != null && Enclosed.class.equals(runWith.value());
    }

    private static void verifyJUnitCategorySupport(ClassLoader applicationClassLoader) {
        boolean failed = false;
        try {
            applicationClassLoader.loadClass("org.junit.experimental.categories.Category");

            // In some cases, we may end up in a situation where we have multiple versions of JUnit
            // on the classpath. Even if we can successfully load Category from the newer version, we
            // need to verify the older has at least Description#getTestClass.
            Class<?> desc = applicationClassLoader.loadClass("org.junit.runner.Description");
            desc.getMethod("getTestClass"); // Added in JUnit 4.6
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            failed = true;
        }

        if (failed) {
            throw new GradleException("JUnit Categories defined but declared JUnit version does not support Categories.");
        }
    }

    private boolean allTestsFiltered(Runner runner, List<Filter> filters) {
        LinkedList<Description> queue = new LinkedList<Description>();
        queue.add(runner.getDescription());
        while (!queue.isEmpty()) {
            Description description = queue.removeFirst();
            queue.addAll(description.getChildren());
            boolean run = true;
            for (Filter filter : filters) {
                if (!filter.shouldRun(description)) {
                    run = false;
                    break;
                }
            }
            if (run) {
                return false;
            }
        }
        return true;
    }

    private static class MethodNameFilter extends org.junit.runner.manipulation.Filter {

        private final TestSelectionMatcher matcher;

        public MethodNameFilter(TestSelectionMatcher matcher) {
            this.matcher = matcher;
        }

        @Override
        public boolean shouldRun(Description description) {
            if (matcher.matchesTest(JUnitTestEventAdapter.className(description), JUnitTestEventAdapter.methodName(description))) {
                return true;
            }

            for (Description child : description.getChildren()) {
                if (shouldRun(child)) {
                    return true;
                }
            }

            return false;
        }

        @Override
        public String describe() {
            return "Includes matching test methods";
        }
    }

    @NullMarked
    private final class FilteredGradleRequest extends Request {
        private final Runner runner;

        private FilteredGradleRequest(Runner filteredRunner) {
            if (spec.isDryRun()) {
                runner = new JUnitTestDryRunner(filteredRunner);
            } else {
                runner = filteredRunner;
            }
        }

        @Override
        public Runner getRunner() {
            return runner;
        }
    }
}
