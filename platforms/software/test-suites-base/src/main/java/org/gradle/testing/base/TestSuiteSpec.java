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
package org.gradle.testing.base;

import org.gradle.api.Incubating;
import org.gradle.platform.base.ComponentSpec;
import org.gradle.platform.base.GeneralComponentSpec;

/**
 * A component representing a suite of tests that will be built and executed together.
 */
@Incubating
public interface TestSuiteSpec extends GeneralComponentSpec {
    /**
     * The tested component.
     */
    ComponentSpec getTestedComponent();

    /**
     * Sets the tested component.
     * @param testedComponent the component under test
     */
    void setTestedComponent(ComponentSpec testedComponent);

    /**
     * Sets the tested component.
     * @param testedComponent the component under test
     */
    void testing(ComponentSpec testedComponent);
}
