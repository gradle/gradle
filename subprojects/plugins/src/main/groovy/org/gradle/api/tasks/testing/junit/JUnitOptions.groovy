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

package org.gradle.api.tasks.testing.junit;

import org.gradle.api.tasks.testing.TestFrameworkOptions;

/**
 * The JUnit specific test options.
 */
public class JUnitOptions extends TestFrameworkOptions {

    /**
     * The set of groups to run.
     */
    Set<String> includeCategories = new HashSet<String>();

    /**
     * The set of groups to exclude.
     */
    Set<String> excludeCategories = new HashSet<String>();


    JUnitOptions includeCategories(String... includeGroups) {
        this.includeCategories.addAll(Arrays.asList(includeGroups));
        this;
    }

    JUnitOptions excludeCategories(String... excludeGroups) {
        this.excludeCategories.addAll(Arrays.asList(excludeGroups));
        this;
    }


}
