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

package org.gradle.api.internal.tasks.testing.junit;

import org.gradle.api.internal.tasks.testing.DefaultTestSuiteDescriptor;
import org.jspecify.annotations.NullMarked;

import java.util.List;

/**
 * A descriptor for a JUnit 4 test suite {@code org.junit.runners.Suite}, which can execute tests in
 * multiple test classes.
 */
@NullMarked
public final class JUnit4TestSuiteDescriptor extends DefaultTestSuiteDescriptor {
    private final List<String> testClasses;

    public JUnit4TestSuiteDescriptor(Object id, String name, List<String> testClasses) {
        super(id, name);
        this.testClasses = testClasses;
    }

    public List<String> getTestClasses() {
        return testClasses;
    }

    @Override
    public String toString() {
        return getName();
    }
}
