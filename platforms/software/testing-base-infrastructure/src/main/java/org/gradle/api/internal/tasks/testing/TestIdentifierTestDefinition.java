/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.api.internal.tasks.testing;

import org.gradle.api.internal.tasks.testing.filter.TestSelectionMatcher;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.junit.platform.engine.TestSource;
import org.junit.platform.engine.support.descriptor.ClassSource;
import org.junit.platform.engine.support.descriptor.FileSource;
import org.junit.platform.engine.support.descriptor.MethodSource;
import org.junit.platform.launcher.TestIdentifier;

import java.util.Objects;

/**
 * A {@link TestDefinition} that wraps a JUnit Platform {@link TestIdentifier}.
 * <p>
 * Values such as class name, method name, and file are extracted on demand from the
 * identifier's {@link TestSource}. These are used by {@link #matches(TestSelectionMatcher)}
 * for daemon-side filtering and by {@link #getClassName()} for previously-failed-first
 * prioritization.
 * <p>
 * The unique ID from the wrapped {@link TestIdentifier} is used as this definition's
 * {@link #getId() id}, and the worker uses it to re-discover the test via
 * {@code selectUniqueId}.
 */
@NullMarked
public final class TestIdentifierTestDefinition implements TestDefinition {
    private final TestIdentifier testIdentifier;

    public TestIdentifierTestDefinition(TestIdentifier testIdentifier) {
        this.testIdentifier = testIdentifier;
    }

    @Override
    public String getId() {
        return testIdentifier.getUniqueId();
    }

    @Override
    public boolean matches(TestSelectionMatcher matcher) {
        TestSource source = testIdentifier.getSource().orElse(null);
        if (source instanceof MethodSource) {
            return matcher.matchesTest(((MethodSource) source).getClassName(), ((MethodSource) source).getMethodName());
        } else if (source instanceof ClassSource) {
            return matcher.mayIncludeClass(((ClassSource) source).getClassName());
        } else if (source instanceof FileSource) {
            return matcher.matchesFile(((FileSource) source).getFile());
        }
        return true;
    }

    @Override
    public String getDisplayName() {
        return testIdentifier.getDisplayName();
    }

    public TestIdentifier getTestIdentifier() {
        return testIdentifier;
    }

    @Nullable
    public String getClassName() {
        TestSource source = testIdentifier.getSource().orElse(null);
        if (source instanceof MethodSource) {
            return ((MethodSource) source).getClassName();
        } else if (source instanceof ClassSource) {
            return ((ClassSource) source).getClassName();
        }
        return null;
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        TestIdentifierTestDefinition that = (TestIdentifierTestDefinition) o;
        return Objects.equals(testIdentifier, that.testIdentifier);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(testIdentifier);
    }

    @Override
    public String toString() {
        return "TestIdentifierTestDefinition{" +
            "testIdentifier=" + testIdentifier +
            '}';
    }
}
