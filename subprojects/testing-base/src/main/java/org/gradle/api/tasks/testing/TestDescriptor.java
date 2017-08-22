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

package org.gradle.api.tasks.testing;

import org.gradle.internal.HasInternalProtocol;

import javax.annotation.Nullable;

/**
 * Describes a test. A test may be a single atomic test, such as the execution of a test method, or it may be a
 * composite test, made up of zero or more tests.
 */
@HasInternalProtocol
public interface TestDescriptor {
    /**
     * Returns the name of the test.  Not guaranteed to be unique.
     *
     * @return The test name
     */
    String getName();

    /**
     * Returns the test class name for this test, if any.
     *
     * @return The class name. May return null.
     */
    @Nullable
    String getClassName();

    /**
     * Is this test a composite test?
     *
     * @return true if this test is a composite test.
     */
    boolean isComposite();

    /**
     * Returns the parent of this test, if any.
     *
     * @return The parent of this test. Null if this test has no parent.
     */
    @Nullable
    TestDescriptor getParent();
}
