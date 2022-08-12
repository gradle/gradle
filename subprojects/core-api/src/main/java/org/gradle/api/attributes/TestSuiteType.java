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

package org.gradle.api.attributes;

import org.gradle.api.Incubating;
import org.gradle.api.Named;

/**
 * Attribute to qualify the type of testing a test suite will perform.
 * <p>
 * This attribute is usually found on variants that have the {@link Category} attribute valued at {@link Category#VERIFICATION verification}.
 * <p>
 * The constant values present here are not exhaustive.  Any value is allowed, so long as that value
 * is only used to categorize a single test suite within each project.
 *
 * @since 7.4
 */
@Incubating
public interface TestSuiteType extends Named {
    Attribute<TestSuiteType> TEST_SUITE_TYPE_ATTRIBUTE = Attribute.of("org.gradle.testsuite.type", TestSuiteType.class);

    /**
     * Unit tests, the default type of test suite
     */
    String UNIT_TEST = "unit-test";

    String INTEGRATION_TEST = "integration-test";

    /**
     * Functional tests, will be added automatically when initializing a new plugin project
     */
    String FUNCTIONAL_TEST = "functional-test";

    String PERFORMANCE_TEST = "performance-test";
}
