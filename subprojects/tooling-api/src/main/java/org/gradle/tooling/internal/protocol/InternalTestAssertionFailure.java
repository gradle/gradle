/*
 * Copyright 2022 the original author or authors.
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
package org.gradle.tooling.internal.protocol;

import javax.annotation.Nullable;

/**
 * DO NOT CHANGE THIS INTERFACE. It is part of the cross-version protocol.
 *
 * Represent a test assertion failure where the test fails due to a broken assertion.
 *
 * @since 7.6
 */
public interface InternalTestAssertionFailure extends InternalTestFailure {

    /**
     * Returns the string representation of the expected value.
     *
     * @return the expected value or {@code null} if the test framework doesn't supply detailed information on assertion failures
     */
    @Nullable
    String getExpected();

    /**
     * Returns the string representation of the actual value.
     *
     * @return the actual value or {@code null} if the test framework doesn't supply detailed information on assertion failures
     */
    @Nullable
    String getActual();
}
