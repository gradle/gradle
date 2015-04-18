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
public interface JvmTestDescriptorVersion1 extends TestDescriptorVersion1 {

    /**
     * Returns the name of the test suite, if any.
     *
     * @return The name of the test suite, can be null.
     */
    String getSuiteName();

    /**
     * Returns the name of the test class, if any.
     *
     * @return The name of the test class, can be null.
     */
    String getClassName();

    /**
     * Returns the name of the test method, if any.
     *
     * @return The name of the test method, can be null.
     */
    String getMethodName();

}
