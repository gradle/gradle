/*
 * Copyright 2012 the original author or authors.
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

import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.testing.TestFrameworkOptions;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * The JUnit specific test options.
 */
public class JUnitOptions extends TestFrameworkOptions {
    private Set<String> includeCategories = new LinkedHashSet<String>();

    private Set<String> excludeCategories = new LinkedHashSet<String>();

    /**
     * Copies the options from the source options into the current one.
     * @since 8.0
     */
    public void copyFrom(JUnitOptions other) {
        replace(this.includeCategories, other.includeCategories);
        replace(this.excludeCategories, other.excludeCategories);
    }

    private static void replace(Set<String> target, Set<String> source) {
        target.clear();
        target.addAll(source);
    }

    public JUnitOptions includeCategories(String... includeCategories) {
        this.includeCategories.addAll(Arrays.asList(includeCategories));
        return this;
    }

    public JUnitOptions excludeCategories(String... excludeCategories) {
        this.excludeCategories.addAll(Arrays.asList(excludeCategories));
        return this;
    }

    /**
     * The set of categories to run.
     */
    @Input
    public Set<String> getIncludeCategories() {
        return includeCategories;
    }

    /**
     * The set of categories to run.
     */
    public void setIncludeCategories(Set<String> includeCategories) {
        this.includeCategories = includeCategories;
    }

    /**
     * The set of categories to exclude.
     */
    @Input
    public Set<String> getExcludeCategories() {
        return excludeCategories;
    }

    /**
     * The set of categories to exclude.
     */
    public void setExcludeCategories(Set<String> excludeCategories) {
        this.excludeCategories = excludeCategories;
    }


}
