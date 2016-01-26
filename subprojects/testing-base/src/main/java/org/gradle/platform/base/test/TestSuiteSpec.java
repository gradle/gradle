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
import org.gradle.platform.base.ComponentSpec;

/**
 * A component representing a suite of tests that will be executed together.
 * @deprecated Use {@link org.gradle.testing.base.TestSuiteSpec instead.}
 */
@Incubating
@Deprecated
public interface TestSuiteSpec extends org.gradle.testing.base.TestSuiteSpec {

    // DO NOT remove the methods below or you will break binary compatibility!

    /**
     * The tested component.
     */
    @Override
    ComponentSpec getTestedComponent();

    /**
     * Sets the tested component. The name of this method is not a regular
     * setter just because of DSL.
     * @param testedComponent the component under test
     */
    @Override
    void testing(ComponentSpec testedComponent);
}
