/*
 * Copyright 2013 the original author or authors.
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
package org.gradle.api.internal.tasks.testing.filter;

import org.gradle.api.InvalidUserDataException;
import org.gradle.api.internal.provider.EagerPropertySupport;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.testing.TestFilter;
import org.gradle.api.tasks.testing.TestFilterInputs;
import org.gradle.internal.instrumentation.api.annotations.BackedByLazyProperty;
import org.gradle.internal.scan.UsedByScanPlugin;

import javax.inject.Inject;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

@UsedByScanPlugin("test-retry")
public class DefaultTestFilter implements TestFilter {

    private TestFilterInputs lazyProperties;
    private final Set<String> commandLineIncludeTestNames = new HashSet<String>();

    @Inject
    @SuppressWarnings("this-escape")
    public DefaultTestFilter(ObjectFactory objectFactory) {
        storeIn(objectFactory.newInstance(TestFilterInputs.class));
    }

    /**
     * Stores the values of this filter in the given lazy properties from now on.
     * Values stored before are not moved, so call this before the filter is configured.
     */
    public void attach(TestFilterInputs lazyProperties) {
        storeIn(lazyProperties);
    }

    private void storeIn(TestFilterInputs lazyProperties) {
        lazyProperties.getFailOnNoMatchingTests().convention(true);
        this.lazyProperties = lazyProperties;
    }

    @Internal
    public TestFilterInputs getLazyProperties() {
        return lazyProperties;
    }

    private static void validateName(String name) {
        if (name == null || name.length() == 0) {
            throw new InvalidUserDataException("Selected test name cannot be null or empty.");
        }
    }

    @Override
    public TestFilter includeTestsMatching(String testNamePattern) {
        validateName(testNamePattern);
        EagerPropertySupport.appendAll(lazyProperties.getIncludePatterns(), Collections.singleton(testNamePattern));
        return this;
    }

    @Override
    public TestFilter excludeTestsMatching(String testNamePattern) {
        validateName(testNamePattern);
        EagerPropertySupport.appendAll(lazyProperties.getExcludePatterns(), Collections.singleton(testNamePattern));
        return this;
    }

    @Override
    public TestFilter includeTest(String className, String methodName) {
        EagerPropertySupport.appendAll(lazyProperties.getIncludePatterns(), Collections.singleton(toPattern(className, methodName)));
        return this;
    }

    @Override
    public TestFilter excludeTest(String className, String methodName) {
        EagerPropertySupport.appendAll(lazyProperties.getExcludePatterns(), Collections.singleton(toPattern(className, methodName)));
        return this;
    }

    private static String toPattern(String className, String methodName) {
        validateName(className);
        if (methodName == null || methodName.trim().isEmpty()) {
            return className;
        }
        return className + "." + methodName;
    }

    @Override
    public void setFailOnNoMatchingTests(boolean failOnNoMatchingTests) {
        lazyProperties.getFailOnNoMatchingTests().set(failOnNoMatchingTests);
    }

    @Override
    @BackedByLazyProperty("lazyProperties.failOnNoMatchingTests")
    public boolean isFailOnNoMatchingTests() {
        return lazyProperties.getFailOnNoMatchingTests().get();
    }

    @Override
    @Input
    @BackedByLazyProperty("lazyProperties.includePatterns")
    public Set<String> getIncludePatterns() {
        return EagerPropertySupport.conventionKeepingView(lazyProperties.getIncludePatterns());
    }

    @Override
    @BackedByLazyProperty("lazyProperties.excludePatterns")
    public Set<String> getExcludePatterns() {
        return EagerPropertySupport.conventionKeepingView(lazyProperties.getExcludePatterns());
    }

    @Override
    public TestFilter setIncludePatterns(String... testNamePatterns) {
        return setFilteringPatterns(lazyProperties.getIncludePatterns(), testNamePatterns);
    }

    @Override
    public TestFilter setExcludePatterns(String... testNamePatterns) {
        return setFilteringPatterns(lazyProperties.getExcludePatterns(), testNamePatterns);
    }

    private TestFilter setFilteringPatterns(SetProperty<String> filter, String... testNamePatterns) {
        for (String name : testNamePatterns) {
            validateName(name);
        }
        filter.set(new LinkedHashSet<>(Arrays.asList(testNamePatterns)));
        return this;
    }

    @Input
    public Set<String> getCommandLineIncludePatterns() {
        return commandLineIncludeTestNames;
    }

    public TestFilter setCommandLineIncludePatterns(Collection<String> testNamePatterns) {
        for (String name : testNamePatterns) {
            validateName(name);
        }
        this.commandLineIncludeTestNames.clear();
        this.commandLineIncludeTestNames.addAll(testNamePatterns);
        return this;
    }

    public TestFilter includeCommandLineTest(String className, String methodName) {
        commandLineIncludeTestNames.add(toPattern(className, methodName));
        return this;
    }

    public TestFilterSpec toSpec() {
        // Patterns bound lazily skipped the validation of the eager methods
        Set<String> includePatterns = validated(lazyProperties.getIncludePatterns().get());
        Set<String> excludePatterns = validated(lazyProperties.getExcludePatterns().get());
        return new TestFilterSpec(includePatterns, excludePatterns, new LinkedHashSet<>(getCommandLineIncludePatterns()));
    }

    private static Set<String> validated(Set<String> patterns) {
        for (String name : patterns) {
            validateName(name);
        }
        return new LinkedHashSet<>(patterns);
    }

    /**
     * Are there any include or exclude filters at all?
     */
    public boolean hasPatterns() {
        return !commandLineIncludeTestNames.isEmpty() || !lazyProperties.getIncludePatterns().get().isEmpty() || !lazyProperties.getExcludePatterns().get().isEmpty();
    }
}
