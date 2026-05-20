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
        if (source.isPresent()) {
            TestSource testSource = source.get();
            if (testSource instanceof MethodSource) {
                return shouldRun(descriptor, (MethodSource) testSource);
            }
            if (testSource instanceof ClassSource) {
                return shouldRun(descriptor, checkingParent, (ClassSource) testSource);
            }
        }

        // Source is absent or of a custom type (e.g. ArchUnit field-based tests).
        // Walk up to the first ancestor with a class source and honor its exclude status:
        // if that enclosing class exactly matches an exclude pattern, this descriptor is also
        // excluded (as a member of the class). Otherwise default to included (original behavior
        // preserved — the filter's status quo for custom sources is inclusive).
        TestDescriptor current = descriptor.getParent().orElse(null);
        while (current != null) {
            Optional<String> enclosingClassName = className(current);
            if (enclosingClassName.isPresent()) {
                return !matcher.matchesExcludeClassExactly(enclosingClassName.get());
            }
            current = current.getParent().orElse(null);
        }
        return true;
    }

    private boolean shouldRun(TestDescriptor descriptor, boolean checkingParent, ClassSource classSource) {
        String className = classSource.getClassName();
        if (matcher.matchesExcludeClassExactly(className)) {
            // This class exactly matches an exclude pattern.
            // Return immediately to prevent children from re-including the container. Ancestors that are
            // themselves included by pattern (e.g. a test suite) are handled by classMatch.
            return false;
        }
        Set<? extends TestDescriptor> children = descriptor.getChildren();
        if (!checkingParent) {
            for (TestDescriptor child : children) {
                if (shouldRun(child)) {
                    return true;
                }
            }
        }
        if (children.isEmpty()) {
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

    /**
     * Walks up the descriptor chain looking for an ancestor class whose name matches an include
     * pattern. An ancestor-level include may re-include a descendant, but an exclude on a nested
     * class must not be bypassed by walking up to its enclosing class.
     *
     * <p>An ancestor is an <em>enclosing class</em> of the excluded class when the excluded
     * class's fully qualified name starts with the ancestor's name followed by {@code $}
     * (e.g. {@code SampleTest} encloses {@code SampleTest$NestedTestClass}). Re-inclusion
     * via an enclosing ancestor is forbidden. Re-inclusion via an unrelated ancestor
     * (e.g. a JUnit Platform suite that references a separate excluded class) is allowed.
     *
     * <p>This asymmetry is intentional:
     * <ul>
     *   <li>{@code --tests Parent} should pull in tests from {@code Parent$Nested} transitively.</li>
     *   <li>{@code excludeTest("Parent$Nested", null)} must not be bypassed because the
     *       enclosing class {@code Parent} happens not to match the exclude.</li>
     *   <li>{@code excludeTestsMatching("Foo")} excludes standalone {@code Foo} tests, but
     *       should still allow {@code Foo} tests to run when wrapped in an unrelated suite.</li>
     * </ul>
     */
    private boolean classMatch(TestDescriptor descriptor) {
        TestDescriptor current = descriptor;
        String methodName = null;
        // Records the lowest-level class in the chain that matches an exclude pattern.
        // Used to block re-inclusion via the walk-up only for enclosing-class ancestors;
        // unrelated ancestors (e.g. test suites) can still re-include.
        String excludedClassName = null;
        while (true) {
            Optional<TestDescriptor> parent = current.getParent();
            if (!parent.isPresent()) {
                break;
            }

            Optional<String> className = className(current);
            if (className.isPresent()) {
                String name = className.get();
                if (excludedClassName == null && matcher.matchesExcludeTest(name, methodName)) {
                    excludedClassName = name;
                }
                // True when this ancestor is an enclosing class of the excluded class
                // (same class, or a $-parent), as opposed to an unrelated ancestor.
                boolean ancestorEnclosesExclude = excludedClassName != null
                    && (excludedClassName.equals(name) || excludedClassName.startsWith(name + "$"));
                if (!ancestorEnclosesExclude && matcher.matchesIncludeTest(name, methodName)) {
                    return true;
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
