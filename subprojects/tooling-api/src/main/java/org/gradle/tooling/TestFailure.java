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

package org.gradle.tooling;

import org.gradle.api.Incubating;

/**
 * Describes a test failure, that can either be a test assertion failure or a test framework failure.
 *
 * @since 7.6
 */
@Incubating
public interface TestFailure extends Failure {

    /**
     * The fully-qualified name of the underlying exception type.
     *
     * @return The exception class name
     */
    String getClassName();

    /**
     * The stringified version of the stacktrace created from the underlying exception.
     *
     * @return the stacktrace
     */
    String getStacktrace();
}
