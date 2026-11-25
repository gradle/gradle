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

import org.gradle.api.Incubating;
import org.gradle.api.internal.provider.EagerPropertySupport;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.testing.TestFrameworkOptions;
import org.gradle.internal.instrumentation.api.annotations.BackedByLazyProperty;
import org.gradle.internal.instrumentation.api.annotations.LazyProperties;
import org.gradle.internal.instrumentation.api.annotations.ToBeReplacedByLazyProperty;

import java.util.Arrays;
import java.util.Set;

/**
 * The JUnit specific test options.
 * @since 0.7
 */
public abstract class JUnitOptions extends TestFrameworkOptions {

    /**
     * The lazy properties behind the eager getters and setters of these options.
     *
     * @since 10.0.0
     */
    @Incubating
    @Internal
    @LazyProperties
    public abstract JUnitOptionsInputs getLazyProperties();

    /**
     * Copies the options from the source options into the current one.
     * @since 8.0
     */
    public void copyFrom(JUnitOptions other) {
        JUnitOptionsInputs source = other.getLazyProperties();
        getLazyProperties().getIncludeCategories().set(source.getIncludeCategories());
        getLazyProperties().getExcludeCategories().set(source.getExcludeCategories());
    }

    /**
     * Include categories.
     *
     * @since 1.6
     */
    public JUnitOptions includeCategories(String... includeCategories) {
        EagerPropertySupport.appendAll(getLazyProperties().getIncludeCategories(), Arrays.asList(includeCategories));
        return this;
    }

    /**
     * Exclude categories.
     *
     * @since 1.6
     */
    public JUnitOptions excludeCategories(String... excludeCategories) {
        EagerPropertySupport.appendAll(getLazyProperties().getExcludeCategories(), Arrays.asList(excludeCategories));
        return this;
    }

    /**
     * The set of categories to run.
     * @since 1.6
     */
    @Input
    @ToBeReplacedByLazyProperty
    @BackedByLazyProperty("lazyProperties.includeCategories")
    public Set<String> getIncludeCategories() {
        return EagerPropertySupport.conventionKeepingViewOrNull(getLazyProperties().getIncludeCategories());
    }

    /**
     * The set of categories to run.
     * @since 1.6
     */
    public void setIncludeCategories(Set<String> includeCategories) {
        EagerPropertySupport.setEager(getLazyProperties().getIncludeCategories(), includeCategories);
    }

    /**
     * The set of categories to exclude.
     * @since 1.6
     */
    @Input
    @ToBeReplacedByLazyProperty
    @BackedByLazyProperty("lazyProperties.excludeCategories")
    public Set<String> getExcludeCategories() {
        return EagerPropertySupport.conventionKeepingViewOrNull(getLazyProperties().getExcludeCategories());
    }

    /**
     * The set of categories to exclude.
     * @since 1.6
     */
    public void setExcludeCategories(Set<String> excludeCategories) {
        EagerPropertySupport.setEager(getLazyProperties().getExcludeCategories(), excludeCategories);
    }
}
