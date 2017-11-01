/*
 * Copyright 2010 the original author or authors.
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

package org.gradle.integtests.fixtures;

public interface TestExecutionResult {
    /**
     * Asserts that the given test classes (and only the given test classes) were executed.
     */
    TestExecutionResult assertTestClassesExecuted(String... testClasses);

    /**
     * Returns the result for the given test class.
     */
    TestClassExecutionResult testClass(String testClass);

    /**
     * Returns the result for the first test class whose name starts with the given string.
     */
    TestClassExecutionResult testClassStartsWith(String testClass);

    int getTotalNumberOfTestClassesExecuted();
}
