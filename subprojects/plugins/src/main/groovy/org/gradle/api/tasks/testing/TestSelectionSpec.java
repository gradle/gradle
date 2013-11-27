/*
 * Copyright 2013 the original author or authors.
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
package org.gradle.api.tasks.testing;

import java.util.Set;

/**
 * Criteria for the test selection
 *
 * @since 1.10
 */
public interface TestSelectionSpec {

    /**
     * Appends a test for selection, wildcard '*' is supported.
     * Examples of test names: com.foo.FooTest.someMethod, com.foo.FooTest, *FooTest*, com.foo*
     *
     * @param name test's name
     * @return this selection object
     */
    TestSelectionSpec name(String name);

    /**
     * Selected test names
     */
    Set<String> getNames();

    /**
     * Sets test names for selection. Wildcard '*' is supported.
     * Examples of test names: com.foo.FooTest.someMethod, com.foo.FooTest, *FooTest*, com.foo*
     *
     * @param names test names
     * @return this selection object
     */
    TestSelectionSpec setNames(String ... names);

}
