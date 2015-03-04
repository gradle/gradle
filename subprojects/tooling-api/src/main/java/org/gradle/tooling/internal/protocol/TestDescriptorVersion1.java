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

package org.gradle.tooling.internal.protocol;

import org.gradle.api.Incubating;

/**
 * DO NOT CHANGE THIS INTERFACE. It is part of the cross-version protocol.
 */
@Incubating
public interface TestDescriptorVersion1 {

    /**
     * Returns the id that uniquely identifies the test.
     *
     * @return The unique id of the test, never null
     */
    Object getId();

    /**
     * Returns the name of the test.
     *
     * @return The name of the test, never null
     */
    String getName();

    /**
     * Returns the name of the test class, if any.
     *
     * @return The name of the test class, can be null
     */
    String getClassName();

    /**
     * Returns the parent of this test, if any.
     *
     * @return The parent of this test, can be null
     */
    TestDescriptorVersion1 getParent();

}
