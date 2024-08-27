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

import org.gradle.api.provider.SetProperty;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.testing.TestFrameworkOptions;
import org.gradle.internal.instrumentation.api.annotations.ReplacesEagerProperty;

/**
 * The JUnit specific test options.
 */
public abstract class JUnitOptions extends TestFrameworkOptions {

    /**
     * Copies the options from the source options into the current one.
     * @since 8.0
     */
    public void copyFrom(JUnitOptions other) {
        getIncludeCategories().set(other.getIncludeCategories());
        getExcludeCategories().set(other.getExcludeCategories());
    }

    public JUnitOptions includeCategories(String... includeCategories) {
        this.getIncludeCategories().addAll(includeCategories);
        return this;
    }

    public JUnitOptions excludeCategories(String... excludeCategories) {
        this.getExcludeCategories().addAll(excludeCategories);
        return this;
    }

    /**
     * The set of categories to run.
     */
    @Input
    @ReplacesEagerProperty
    public abstract SetProperty<String> getIncludeCategories();

    /**
     * The set of categories to exclude.
     */
    @Input
    @ReplacesEagerProperty
    public abstract SetProperty<String> getExcludeCategories();
}
