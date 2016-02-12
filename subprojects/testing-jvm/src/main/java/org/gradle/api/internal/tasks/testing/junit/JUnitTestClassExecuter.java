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

import org.gradle.internal.concurrent.ThreadSafe;
import org.junit.runner.Description;
import org.junit.runner.Request;
import org.junit.runner.Runner;
import org.junit.runner.manipulation.Filter;
import org.junit.runner.manipulation.Filterable;
import org.junit.runner.manipulation.NoTestsRemainException;
import org.junit.runner.notification.RunListener;
import org.junit.runner.notification.RunNotifier;

import java.util.LinkedList;
import java.util.List;

public class JUnitTestClassExecuter {
    private final ClassLoader applicationClassLoader;
    private final RunListener listener;

    private final JUnitTestFilter testFilter;

    private final TestClassExecutionListener executionListener;

    public JUnitTestClassExecuter(ClassLoader applicationClassLoader, JUnitSpec spec, RunListener listener, TestClassExecutionListener executionListener) {
        assert executionListener instanceof ThreadSafe;
        this.applicationClassLoader = applicationClassLoader;
        this.listener = listener;
        this.testFilter = new JUnitTestFilter(spec, applicationClassLoader);
        this.executionListener = executionListener;
    }

    public void execute(String testClassName) {
        executionListener.testClassStarted(testClassName);

        Throwable failure = null;
        try {
            applyFiltersAndRunTestClass(testClassName);
        } catch (Throwable throwable) {
            failure = throwable;
        }

        executionListener.testClassFinished(failure);
    }

    private void applyFiltersAndRunTestClass(String testClassName) throws ClassNotFoundException {
        Runner runner = getRunner(testClassName);

        List<Filter> filters = new LinkedList<Filter>();

        filters.addAll(testFilter.getCategoryFilters());
        filters.addAll(testFilter.getTestFilters(testClassName, runner.getDescription().isSuite()));

        if(haveTestsToRun(runner, filters)){
            runTestClass(runner);
        }

    }

    private void runTestClass(Runner runner){
        RunNotifier notifier = new RunNotifier();
        notifier.addListener(listener);
        runner.run(notifier);
    }

    private boolean haveTestsToRun(Runner runner, List<Filter> filters){
        if (runner instanceof Filterable) {
            Filterable filterable = (Filterable) runner;
            for (Filter filter : filters) {
                try {
                    filterable.filter(filter);
                } catch (NoTestsRemainException e) {
                    // Ignore
                    return false;
                }
            }
        } else if (allTestsFiltered(runner, filters)) {
            return false;
        }

        return true;
    }

    private Runner getRunner(String testClassName) throws ClassNotFoundException {
        final Class<?> testClass = Class.forName(testClassName, false, applicationClassLoader);
        Request request = Request.aClass(testClass);
        return request.getRunner();
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

}
