/*
 * Copyright 2016 the original author or authors.
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

import com.beust.jcommander.internal.Lists;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Transformer;
import org.gradle.api.internal.tasks.testing.filter.TestSelectionMatcher;
import org.gradle.util.CollectionUtils;
import org.junit.runner.Description;
import org.junit.runner.manipulation.Filter;

import java.util.List;

class JUnitTestFilter {
    private final JUnitSpec options;
    private final ClassLoader applicationClassLoader;

    public JUnitTestFilter(JUnitSpec options, ClassLoader applicationClassLoader) {
        this.options = options;
        this.applicationClassLoader = applicationClassLoader;
    }

    List<Filter> getCategoryFilters(){

        List<Filter> categoryFilters = Lists.newArrayList();

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
            categoryFilters.add(new CategoryFilter(
                CollectionUtils.collect(options.getIncludeCategories(), transformer),
                CollectionUtils.collect(options.getExcludeCategories(), transformer)
            ));
        }

        return categoryFilters;
    }


    List<Filter> getTestFilters(String testClassName, boolean isSuite){

        List<Filter> includeExcludeFilters = Lists.newArrayList();

        if (options.hasTestConfiguration()) {
            TestSelectionMatcher matcher = new TestSelectionMatcher(options.getIncludedTests(), options.getExcludedTests());

            // For test suites (including suite-like custom Runners), if the test suite class
            // matches the filter, run the entire suite instead of filtering away its contents.
            if (!isSuite || !matcher.matchesTest(testClassName, null)) {
                includeExcludeFilters.add(new MethodNameIncludeFilter(matcher));
            }
        }

        return includeExcludeFilters;

    }


    private static class MethodNameIncludeFilter extends org.junit.runner.manipulation.Filter {

        private final TestSelectionMatcher matcher;
        private static final String FILTER_DESCRIPTION = "Includes matching test methods";

        public MethodNameIncludeFilter(TestSelectionMatcher matcher) {
            this.matcher = matcher;
        }


        protected boolean matchCheck(Description description) {
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
        public boolean shouldRun(Description description) {
            return matchCheck(description);
        }

        public String describe(){
            return FILTER_DESCRIPTION;
        }
    }

}
