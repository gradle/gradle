/*
 * Copyright 2014 the original author or authors.
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
import org.gradle.platform.base.BinarySpec;
import org.gradle.platform.base.ComponentSpec;

/**
 * A component representing a suite of tests that will be executed together.
 *
 * @param <T> The type of the {@link org.gradle.platform.base.BinarySpec} associated with
 * the TestSuiteSpec.
 */
@Incubating
public interface TestSuiteSpec<T extends BinarySpec> extends ComponentSpec<T> {
    /**
     * The tested component.
     */
    ComponentSpec<T> getTestedComponent();
}
