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

import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.internal.tasks.testing.filter.TestFilterSpec;
import org.gradle.api.internal.tasks.testing.filter.TestSelectionMatcher;
import org.gradle.api.tasks.testing.TestFailure;
import org.gradle.internal.concurrent.ThreadSafe;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.Description;
import org.junit.runner.Request;
import org.junit.runner.RunWith;
import org.junit.runner.Runner;
import org.junit.runner.manipulation.Filter;
import org.junit.runner.manipulation.Filterable;
import org.junit.runner.manipulation.NoTestsRemainException;
import org.junit.runner.notification.RunListener;
import org.junit.runner.notification.RunNotifier;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class JUnitTestClassExecutor implements Action<String> {
    private final ClassLoader applicationClassLoader;
    private final RunListener listener;
    private final JUnitSpec spec;
    private final TestClassExecutionListener executionListener;

    public JUnitTestClassExecutor(ClassLoader applicationClassLoader, JUnitSpec spec, RunListener listener, TestClassExecutionListener executionListener) {
        assert executionListener instanceof ThreadSafe;
        this.applicationClassLoader = applicationClassLoader;
        this.listener = listener;
        this.spec = spec;
        this.executionListener = executionListener;
    }

    @Override
    public void execute(String testClassName) {
        executionListener.testClassStarted(testClassName);
        try {
            runTestClass(testClassName);
            executionListener.testClassFinished(null);
        } catch (Throwable throwable) {
            executionListener.testClassFinished(TestFailure.fromTestFrameworkFailure(throwable));
        }
    }

    private void runTestClass(String testClassName) throws ClassNotFoundException {
        final Class<?> testClass = Class.forName(testClassName, false, applicationClassLoader);
        if (isNestedClassInsideEnclosedRunner(testClass)) {
            return;
        }
        List<Filter> filters = new ArrayList<Filter>();
        if (spec.hasCategoryConfiguration()) {
            verifyJUnitCategorySupport();
            filters.add(new CategoryFilter(spec.getIncludeCategories(), spec.getExcludeCategories(), applicationClassLoader));
        }

        Request request = Request.aClass(testClass);
        Runner runner = request.getRunner();

        TestFilterSpec filterSpec = spec.getFilter();
        if (!filterSpec.getIncludedTests().isEmpty()
            || !filterSpec.getIncludedTestsCommandLine().isEmpty()
            || !filterSpec.getExcludedTests().isEmpty()) {
            TestSelectionMatcher matcher = new TestSelectionMatcher(filterSpec);

            // For test suites (including suite-like custom Runners), if the test suite class
            // matches the filter, run the entire suite instead of filtering away its contents.
            if (!runner.getDescription().isSuite() || !matcher.matchesTest(testClassName, null)) {
                filters.add(new MethodNameFilter(matcher));
            }
        }

        if (runner instanceof Filterable) {
            Filterable filterable = (Filterable) runner;
            for (Filter filter : filters) {
                try {
                    filterable.filter(filter);
                } catch (NoTestsRemainException e) {
                    // Ignore
                    return;
                }
            }
        } else if (allTestsFiltered(runner, filters)) {
            return;
        }

        RunNotifier notifier = new RunNotifier();
        notifier.addListener(listener);
        runner.run(notifier);
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

    private void verifyJUnitCategorySupport() {
        boolean failed = false;
        try {
            applicationClassLoader.loadClass("org.junit.experimental.categories.Category");

            // In some cases, we may end up in a situation where we have multiple versions of JUnit
            // on the classpath. Even if we can successfully load Category from the newer version, we
            // need to verify the older has at least Description#getTestClass.
            Class<?> desc = applicationClassLoader.loadClass("org.junit.runner.Description");
            desc.getMethod("getTestClass"); // Added in JUnit 4.6
        } catch (ClassNotFoundException e) {
            failed = true;
        } catch (NoSuchMethodException e) {
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
}
