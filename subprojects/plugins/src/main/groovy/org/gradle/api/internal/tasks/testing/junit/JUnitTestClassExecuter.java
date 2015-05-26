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

import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Transformer;
import org.gradle.api.internal.tasks.testing.filter.TestSelectionMatcher;
import org.gradle.internal.concurrent.ThreadSafe;
import org.gradle.util.CollectionUtils;
import org.junit.runner.Description;
import org.junit.runner.Request;
import org.junit.runner.Runner;
import org.junit.runner.notification.RunListener;
import org.junit.runner.notification.RunNotifier;

public class JUnitTestClassExecuter {
    private final ClassLoader applicationClassLoader;
    private final RunListener listener;
    private final JUnitSpec options;
    private final TestClassExecutionListener executionListener;

    public JUnitTestClassExecuter(ClassLoader applicationClassLoader, JUnitSpec spec, RunListener listener, TestClassExecutionListener executionListener) {
        assert executionListener instanceof ThreadSafe;
        this.applicationClassLoader = applicationClassLoader;
        this.listener = listener;
        this.options = spec;
        this.executionListener = executionListener;
    }

    public void execute(String testClassName) {
        executionListener.testClassStarted(testClassName);

        Throwable failure = null;
        try {
            runTestClass(testClassName);
        } catch (Throwable throwable) {
            failure = throwable;
        }

        executionListener.testClassFinished(failure);
    }

    private void runTestClass(String testClassName) throws ClassNotFoundException {
        final Class<?> testClass = Class.forName(testClassName, false, applicationClassLoader);
        Request request = Request.aClass(testClass);
        if (options.hasCategoryConfiguration()) {
            Transformer<Class<?>, String> transformer = new Transformer<Class<?>, String>() {
                public Class<?> transform(final String original) {
                    try {
                        return applicationClassLoader.loadClass(original);
                    } catch (ClassNotFoundException e) {
                        throw new InvalidUserDataException(String.format("Can't load category class [%s].", original), e);
                    }
                }
            };
            request = request.filterWith(new CategoryFilter(
                    CollectionUtils.collect(options.getIncludeCategories(), transformer),
                    CollectionUtils.collect(options.getExcludeCategories(), transformer)
            ));
        }

        if (!options.getIncludedTests().isEmpty()) {
            request = request.filterWith(new MethodNameFilter(options.getIncludedTests()));
        }

        Runner runner = request.getRunner();
        //In case of no matching methods junit will return a ErrorReportingRunner for org.junit.runner.manipulation.Filter.class.
        //Will be fixed with adding class filters
        if (!org.junit.runner.manipulation.Filter.class.getName().equals(runner.getDescription().getDisplayName())) {
            RunNotifier notifier = new RunNotifier();
            notifier.addListener(listener);
            runner.run(notifier);
        }
    }

    private static class MethodNameFilter extends org.junit.runner.manipulation.Filter {

        private final TestSelectionMatcher matcher;

        public MethodNameFilter(Iterable<String> includedTests) {
            matcher = new TestSelectionMatcher(includedTests);
        }

        @Override
        public boolean shouldRun(Description description) {
            return matcher.matchesTest(description.getClassName(), description.getMethodName());
        }

        public String describe() {
            return "Includes matching test methods";
        }
    }
}
