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

import org.apache.commons.lang.StringUtils;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Transformer;
import org.gradle.internal.concurrent.ThreadSafe;
import org.gradle.util.CollectionUtils;
import org.junit.experimental.categories.Category;
import org.junit.internal.runners.ErrorReportingRunner;
import org.junit.runner.Description;
import org.junit.runner.Request;
import org.junit.runner.Runner;
import org.junit.runner.manipulation.Filter;
import org.junit.runner.notification.RunListener;
import org.junit.runner.notification.RunNotifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class JUnitTestClassExecuter {
    private static final Logger LOGGER = LoggerFactory.getLogger(JUnitTestClassProcessor.class);
    private final ClassLoader applicationClassLoader;
    private final RunListener listener;
    private final JUnitSpec options;
    private final TestClassExecutionListener executionListener;

    public JUnitTestClassExecuter(ClassLoader applicationClassLoader, JUnitSpec options, RunListener listener, TestClassExecutionListener executionListener) {

        assert executionListener instanceof ThreadSafe;
        this.applicationClassLoader = applicationClassLoader;
        this.listener = listener;
        this.options = options;
        this.executionListener = executionListener;
    }

    public JUnitTestClassExecuter(ClassLoader applicationClassLoader, RunListener listener, TestClassExecutionListener executionListener) {

        this(applicationClassLoader, null, listener, executionListener);
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
        final Class<?> testClass = Class.forName(testClassName, true, applicationClassLoader);
        Request request = Request.aClass(testClass);

        if (options != null) {

            Transformer<Class<?>, String> transformer = new Transformer<Class<?>, String>() {

                public Class<?> transform(final String original) {
                    try {
                        return applicationClassLoader.loadClass(original);
                    } catch (ClassNotFoundException e) {
                        throw new InvalidUserDataException("Can't load category class.", e);
                    }
                }
            };

            request = request.filterWith(new CategoryFilter(
                    CollectionUtils.collect(options.getIncludeCategories(), transformer),
                    CollectionUtils.collect(options.getExcludeCategories(), transformer)
            ));
        }

        Runner runner = request.getRunner();
        if (!Filter.class.equals(runner.getClass())){
            RunNotifier notifier = new RunNotifier();
            notifier.addListener(listener);
            runner.run(notifier);
        }
    }

    public static class CategoryFilter extends Filter {

        private final Set<Class<?>> inclusions;
        private final Set<Class<?>> exclusions;

        public CategoryFilter(final Set<Class<?>> inclusions, final Set<Class<?>> exclusions) {
            this.inclusions = inclusions;
            this.exclusions = exclusions;
        }

        @Override
        public boolean shouldRun(Description description) {
            return shouldRun(description, description.isSuite() ? null : Description.createSuiteDescription(description.getTestClass()));
        }

        private boolean shouldRun(Description description, Description parent) {

            final Set<Class<?>> categories = new HashSet<Class<?>>();
            Category annotation = description.getAnnotation(Category.class);
            if (annotation != null) {
                categories.addAll(Arrays.asList(annotation.value()));
            }

            if (parent != null) {
                annotation = parent.getAnnotation(Category.class);
                if (annotation != null) {
                    categories.addAll(Arrays.asList(annotation.value()));
                }
            }

            boolean result = inclusions.isEmpty();


            for (Class<?> category : categories) {
                if (matches(category, inclusions)) {
                    result = true;
                    break;
                }
            }

            if (result) {
                for (Class<?> category : categories) {
                    if (matches(category, exclusions)) {
                        result = false;
                        break;
                    }
                }
            }

            return result;

        }

        private boolean matches(final Class<?> category, final Set<Class<?>> categories) {
            for (Class<?> cls : categories) {
                if (cls.isAssignableFrom(category)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public String describe() {
            StringBuilder sb = new StringBuilder();
            if (!inclusions.isEmpty()) {
                sb.append("(");
                sb.append(StringUtils.join(inclusions, " OR "));
                sb.append(")");
                if (!exclusions.isEmpty()) {
                    sb.append(" AND ");
                }
            }
            if (!exclusions.isEmpty()) {
                sb.append("NOT (");
                sb.append(StringUtils.join(exclusions, " OR "));
                sb.append(")");
            }

            return sb.toString();
        }
    }
}
