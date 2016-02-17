/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.platform.base.test;

import org.gradle.api.Incubating;

/**
 * A binary which runs a suite of tests.
 * @deprecated Use {@link org.gradle.testing.base.TestSuiteBinarySpec} instead.
 */
@Incubating
@Deprecated
public interface TestSuiteBinarySpec extends org.gradle.testing.base.TestSuiteBinarySpec {

    /**
     * Returns the test suite that this binary belongs to.
     */
    // DO NOT REMOVE this method or you will break binary compatibility
    @Override
    TestSuiteSpec getTestSuite();

}
