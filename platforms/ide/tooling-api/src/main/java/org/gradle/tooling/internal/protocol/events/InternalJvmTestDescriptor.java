/*
 * Copyright 2015 the original author or authors.
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
package org.gradle.tooling.internal.protocol.events;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * DO NOT CHANGE THIS INTERFACE. It is part of the cross-version protocol.
 *
 * @since 2.4
 */
@NullMarked
public interface InternalJvmTestDescriptor extends InternalTestDescriptor {

    String KIND_SUITE = "SUITE";
    String KIND_ATOMIC = "ATOMIC";

    /**
     * Returns the kind of test this is. See the constants on this interface for the supported kinds.
     *
     * @return The test kind (test suite, atomic test, etc.).
     */
    String getTestKind();

    /**
     * Returns the name of the test suite, if any.
     *
     * @return The name of the test suite, can be null.
     */
    @Nullable
    String getSuiteName();

    /**
     * Returns the name of the test class, if any.
     *
     * @return The name of the test class, can be null.
     */
    @Nullable
    String getClassName();

    /**
     * Returns the name of the test method, if any.
     *
     * @return The name of the test method, can be null.
     */
    @Nullable
    String getMethodName();

}
