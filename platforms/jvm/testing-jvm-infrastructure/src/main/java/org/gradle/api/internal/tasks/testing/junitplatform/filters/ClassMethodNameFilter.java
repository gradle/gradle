/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.api.internal.tasks.testing.junitplatform.filters;

import org.gradle.api.internal.tasks.testing.filter.TestSelectionMatcher;
import org.jspecify.annotations.NullMarked;
import org.junit.platform.engine.FilterResult;
import org.junit.platform.engine.TestDescriptor;
import org.junit.platform.engine.TestSource;
import org.junit.platform.engine.support.descriptor.ClassSource;
import org.junit.platform.engine.support.descriptor.MethodSource;
import org.junit.platform.launcher.PostDiscoveryFilter;

import java.util.Optional;
import java.util.Set;

/**
 * A JUnit Platform {@link PostDiscoveryFilter} filter that includes or excludes
 * class or method based tests based on their fully qualified names.
 */
@NullMarked
public final class ClassMethodNameFilter implements PostDiscoveryFilter {
    private final TestSelectionMatcher matcher;

    public ClassMethodNameFilter(TestSelectionMatcher matcher) {
        this.matcher = matcher;
    }

    @Override
    public FilterResult apply(TestDescriptor descriptor) {
        if (classMatch(descriptor)) {
            return FilterResult.included("Class match");
        }
        return FilterResult.includedIf(shouldRun(descriptor), () -> "Method or class match", () -> "Method or class mismatch");
    }

    private boolean shouldRun(TestDescriptor descriptor) {
        return shouldRun(descriptor, false);
    }

    private boolean shouldRun(TestDescriptor descriptor, boolean checkingParent) {
        Optional<TestSource> source = descriptor.getSource();
        if (!source.isPresent()) {
            return true;
        }

        TestSource testSource = source.get();
        if (testSource instanceof MethodSource) {
            return shouldRun(descriptor, (MethodSource) testSource);
        }

        if (testSource instanceof ClassSource) {
            return shouldRun(descriptor, checkingParent, (ClassSource) testSource);
        }

        Optional<TestDescriptor> parent = descriptor.getParent();
        return parent.isPresent() && shouldRun(parent.get(), true);
    }

    private boolean shouldRun(TestDescriptor descriptor, boolean checkingParent, ClassSource classSource) {
        Set<? extends TestDescriptor> children = descriptor.getChildren();
        if (!checkingParent) {
            for (TestDescriptor child : children) {
                if (shouldRun(child)) {
                    return true;
                }
            }
        }
        if (children.isEmpty()) {
            String className = classSource.getClassName();
            return matcher.matchesTest(className, null)
                || matcher.matchesTest(className, descriptor.getLegacyReportingName());
        }
        return true;
    }

    private boolean shouldRun(TestDescriptor descriptor, MethodSource methodSource) {
        String methodName = methodSource.getMethodName();
        return matcher.matchesTest(methodSource.getClassName(), methodName)
            || matchesParentMethod(descriptor, methodName);
    }

    private boolean matchesParentMethod(TestDescriptor descriptor, String methodName) {
        return descriptor.getParent()
            .flatMap(this::className)
            .filter(className -> matcher.matchesTest(className, methodName))
            .isPresent();
    }

    private boolean classMatch(TestDescriptor descriptor) {
        TestDescriptor current = descriptor;
        String methodName = null;
        while (true) {

            Optional<TestDescriptor> parent = current.getParent();
            if (!parent.isPresent()) {
                break;
            }

            // If the current descriptor is a class, check if it matches the test selection criteria
            Optional<String> className = className(current);
            if (className.isPresent()) {
                if (matcher.matchesTest(className.get(), methodName)) {
                    return true;
                }

                // If the current descriptor is a class, and it matches an exclude pattern, we can skip checking its parents
                if (matcher.mayExcludeClass(className.get())) {
                    return false;
                }
            }

            // If the descriptor is a MethodSource, capture the method name to use when checking against parent class names
            // (for instance, if the method is in a nested class).
            if (current.getSource().isPresent() && current.getSource().get() instanceof MethodSource) {
                methodName = ((MethodSource) current.getSource().get()).getMethodName();
            }

            current = parent.get();
        }
        return false;
    }

    private Optional<String> className(TestDescriptor descriptor) {
        return descriptor.getSource()
            .filter(ClassSource.class::isInstance)
            .map(ClassSource.class::cast)
            .map(ClassSource::getClassName);
    }
}
