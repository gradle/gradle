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
import org.junit.platform.launcher.TestIdentifier;

import java.util.Objects;

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
        // TODO: for now, MATCH ALL THE THINGS!
        return true;
    }

    @Override
    public String getDisplayName() {
        return testIdentifier.getDisplayName();
    }

    public TestIdentifier getTestIdentifier() {
        return testIdentifier;
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
