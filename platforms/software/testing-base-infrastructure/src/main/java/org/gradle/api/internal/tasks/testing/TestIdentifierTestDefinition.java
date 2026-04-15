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
import org.junit.platform.engine.support.descriptor.MethodSource;
import org.junit.platform.launcher.TestIdentifier;

import java.util.Objects;

/**
 * A {@link TestDefinition} that wraps a JUnit Platform {@link TestIdentifier}.
 * <p>
 * At construction time, extracts the class name and method name from the identifier's
 * {@link TestSource} (if it is a {@link MethodSource} or {@link ClassSource}). These
 * are used by {@link #matches(TestSelectionMatcher)} for daemon-side {@code --tests}
 * filtering and by {@link #getClassName()} for previously-failed-first prioritization.
 * <p>
 * The unique ID from the wrapped {@link TestIdentifier} is used as this definition's
 * {@link #getId() id}, and the worker uses it to re-discover the test via
 * {@code selectUniqueId}.
 */
@NullMarked
public final class TestIdentifierTestDefinition implements TestDefinition {
    private final TestIdentifier testIdentifier;
    @Nullable private final String className;
    @Nullable private final String methodName;

    public TestIdentifierTestDefinition(TestIdentifier testIdentifier) {
        this.testIdentifier = testIdentifier;
        TestSource source = testIdentifier.getSource().orElse(null);
        if (source instanceof MethodSource) {
            this.className = ((MethodSource) source).getClassName();
            this.methodName = ((MethodSource) source).getMethodName();
        } else if (source instanceof ClassSource) {
            this.className = ((ClassSource) source).getClassName();
            this.methodName = null;
        } else {
            this.className = null;
            this.methodName = null;
        }
    }

    @Override
    public String getId() {
        return testIdentifier.getUniqueId();
    }

    @Override
    public boolean matches(TestSelectionMatcher matcher) {
        if (className == null) {
            return true;
        }
        return matcher.matchesTest(className, methodName);
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
        return className;
    }

    @Override
    public boolean equals(Object o) {
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
