/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.api.tasks.testing

import org.gradle.api.tasks.compile.AbstractOptions
import org.gradle.api.GradleException

/**
 * @author Tom Eyckmans
 */

public abstract class AbstractTestFrameworkOptions extends AbstractOptions {
    protected AbstractTestFramework testFramework;

    protected AbstractTestFrameworkOptions(AbstractTestFramework testFramework) {
        if (testFramework == null) throw new IllegalArgumentException("testFramework == null!")

        this.testFramework = testFramework;
    }

    public def propertyMissing(String name) {
        throw new GradleException(
                """
            Property ${name} could not be found in the options of the ${testFramework.name} test framework.

            ${AbstractTestFramework.USE_OF_CORRECT_TEST_FRAMEWORK}
            """);
    }

    public def methodMissing(String name, args) {
        throw new GradleException(
                """
            Method ${name} could not be found in the options of the ${testFramework.name} test framework.

            ${AbstractTestFramework.USE_OF_CORRECT_TEST_FRAMEWORK}
            """);
    }
}
