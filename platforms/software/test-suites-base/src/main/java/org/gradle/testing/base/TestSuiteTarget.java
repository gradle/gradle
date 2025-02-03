/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.testing.base;

import org.gradle.api.Incubating;
import org.gradle.api.file.Directory;
import org.gradle.api.provider.Provider;

/**
 * Base test suite target.
 *
 * A test suite target is a collection of tests that run in a particular context (operating system, Java runtime, etc).
 *
 * @since 7.3
 */
@Incubating
public interface TestSuiteTarget {

    /**
     * The directory containing the binary results produced by executing this test suite target.
     *
     * @return the binary results directory
     *
     * @since 8.13
     */
    Provider<Directory> getBinaryResultsDirectory();
}
