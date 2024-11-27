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
import org.gradle.api.provider.Property;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.testing.TestFilter;
import org.gradle.internal.instrumentation.api.annotations.ReplacesEagerProperty;
import org.gradle.internal.instrumentation.api.annotations.ReplacesEagerProperty.BinaryCompatibility;
import org.gradle.internal.scan.UsedByScanPlugin;

import java.util.LinkedHashSet;

@UsedByScanPlugin("test-retry")
public abstract class DefaultTestFilter implements TestFilter {

    public DefaultTestFilter() {
        getFailOnNoMatchingTests().convention(true);
    }

    @Override
    public TestFilter includeTestsMatching(String testNamePattern) {
        validateName(testNamePattern);
        getIncludePatterns().add(testNamePattern);
        return this;
    }

    @Override
    public TestFilter excludeTestsMatching(String testNamePattern) {
        validateName(testNamePattern);
        getExcludePatterns().add(testNamePattern);
        return this;
    }

    @Override
    public TestFilter includeTest(String className, String methodName) {
        return addToFilteringSet(getIncludePatterns(), className, methodName);
    }

    @Override
    public TestFilter excludeTest(String className, String methodName) {
        return addToFilteringSet(getExcludePatterns(), className, methodName);
    }

    private TestFilter addToFilteringSet(SetProperty<String> filter, String className, String methodName) {
        validateName(className);
        if (methodName == null || methodName.trim().isEmpty()) {
            filter.add(className + ".*");
        } else {
            filter.add(className + "." + methodName);
        }
        return this;
    }

    @Override
    public abstract Property<Boolean> getFailOnNoMatchingTests();

    @Override
    @Input
    public abstract SetProperty<String> getIncludePatterns();

    @Override
    public abstract SetProperty<String> getExcludePatterns();

    /**
     * This is internal property, but it's annotated with @ReplacesEagerProperty too,
     * since some plugins, e.g. KGP, use it.
     */
    @Input
    @ReplacesEagerProperty(
        fluentSetter = true,
        // Kept, since internal classes are not reported in binary checks
        // so the upgrade check reports an error that original methods was not removed
        binaryCompatibility = BinaryCompatibility.ACCESSORS_KEPT
    )
    public abstract SetProperty<String> getCommandLineIncludePatterns();

    public TestFilter includeCommandLineTest(String className, String methodName) {
        return addToFilteringSet(getCommandLineIncludePatterns(), className, methodName);
    }

    public TestFilterSpec toSpec() {
        return new TestFilterSpec(
            // TestFilterSpec get serialized to worker, so we create a copy of original set,
            // to avoid serialization issues on worker for ImmutableSet that SetProperty returns
            new LinkedHashSet<>(getIncludePatterns().get()),
            new LinkedHashSet<>(getExcludePatterns().get()),
            new LinkedHashSet<>(getCommandLineIncludePatterns().get())
        );
    }

    public void validate() {
        for (String name : getIncludePatterns().get()) {
            validateName(name);
        }
        for (String name : getExcludePatterns().get()) {
            validateName(name);
        }
        for (String name : getCommandLineIncludePatterns().get()) {
            validateName(name);
        }
    }

    private static void validateName(String name) {
        if (name == null || name.isEmpty()) {
            throw new InvalidUserDataException("Selected test name cannot be null or empty.");
        }
    }
}
