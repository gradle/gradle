/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.api.tasks.testing.junitplatform;

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
 * The JUnit platform specific test options.
 *
 * @see <a href="https://junit.org/junit5/docs/current/user-guide">JUnit 5 User Guide</a>
 * @since 4.6
 */
public abstract class JUnitPlatformOptions extends TestFrameworkOptions {

    /**
     * The lazy properties behind the eager getters and setters of these options.
     *
     * @since 10.0.0
     */
    @Incubating
    @Internal
    @LazyProperties
    public abstract JUnitPlatformOptionsInputs getLazyProperties();

    /**
     * Copies the options from the source options into the current one.
     * @since 8.0
     */
    public void copyFrom(JUnitPlatformOptions other) {
        JUnitPlatformOptionsInputs source = other.getLazyProperties();
        JUnitPlatformOptionsInputs target = getLazyProperties();
        target.getIncludeEngines().set(source.getIncludeEngines());
        target.getExcludeEngines().set(source.getExcludeEngines());
        target.getIncludeTags().set(source.getIncludeTags());
        target.getExcludeTags().set(source.getExcludeTags());
    }

    /**
     * The set of engines to run with.
     *
     * @see <a href="https://junit.org/junit5/docs/current/user-guide/#launcher-api-engines-custom">Test Engine</a>
     * @since 4.6
     */
    public JUnitPlatformOptions includeEngines(String... includeEngines) {
        EagerPropertySupport.appendAll(getLazyProperties().getIncludeEngines(), Arrays.asList(includeEngines));
        return this;
    }

    /**
     * The set of tags to run with.
     *
     * @see <a href="https://junit.org/junit5/docs/current/user-guide/#writing-tests-tagging-and-filtering">Tagging and Filtering</a>
     * @since 4.6
     */
    public JUnitPlatformOptions includeTags(String... includeTags) {
        EagerPropertySupport.appendAll(getLazyProperties().getIncludeTags(), Arrays.asList(includeTags));
        return this;
    }

    /**
     * The set of engines to exclude.
     *
     * @see <a href="https://junit.org/junit5/docs/current/user-guide/#launcher-api-engines-custom">Test Engine</a>
     * @since 4.6
     */
    public JUnitPlatformOptions excludeEngines(String... excludeEngines) {
        EagerPropertySupport.appendAll(getLazyProperties().getExcludeEngines(), Arrays.asList(excludeEngines));
        return this;
    }

    /**
     * The set of tags to exclude.
     *
     * @see <a href="https://junit.org/junit5/docs/current/user-guide/#writing-tests-tagging-and-filtering">Tagging and Filtering</a>
     * @since 4.6
     */
    public JUnitPlatformOptions excludeTags(String... excludeTags) {
        EagerPropertySupport.appendAll(getLazyProperties().getExcludeTags(), Arrays.asList(excludeTags));
        return this;
    }

    /**
     * Returns the include engines.
     *
     * @since 4.6
     */
    @Input
    @ToBeReplacedByLazyProperty
    @BackedByLazyProperty("lazyProperties.includeEngines")
    public Set<String> getIncludeEngines() {
        return EagerPropertySupport.conventionKeepingViewOrNull(getLazyProperties().getIncludeEngines());
    }

    /**
     * Returns the include tags.
     *
     * @since 4.6
     */
    @Input
    @ToBeReplacedByLazyProperty
    @BackedByLazyProperty("lazyProperties.includeTags")
    public Set<String> getIncludeTags() {
        return EagerPropertySupport.conventionKeepingViewOrNull(getLazyProperties().getIncludeTags());
    }

    /**
     * Sets the include engines.
     *
     * @since 4.6
     */
    public void setIncludeEngines(Set<String> includeEngines) {
        EagerPropertySupport.setEager(getLazyProperties().getIncludeEngines(), includeEngines);
    }

    /**
     * Returns the exclude engines.
     *
     * @since 4.6
     */
    @Input
    @ToBeReplacedByLazyProperty
    @BackedByLazyProperty("lazyProperties.excludeEngines")
    public Set<String> getExcludeEngines() {
        return EagerPropertySupport.conventionKeepingViewOrNull(getLazyProperties().getExcludeEngines());
    }

    /**
     * Sets the exclude engines.
     *
     * @since 4.6
     */
    public void setExcludeEngines(Set<String> excludeEngines) {
        EagerPropertySupport.setEager(getLazyProperties().getExcludeEngines(), excludeEngines);
    }

    /**
     * Sets the include tags.
     *
     * @since 4.6
     */
    public void setIncludeTags(Set<String> includeTags) {
        EagerPropertySupport.setEager(getLazyProperties().getIncludeTags(), includeTags);
    }

    /**
     * Returns the exclude tags.
     *
     * @since 4.6
     */
    @Input
    @ToBeReplacedByLazyProperty
    @BackedByLazyProperty("lazyProperties.excludeTags")
    public Set<String> getExcludeTags() {
        return EagerPropertySupport.conventionKeepingViewOrNull(getLazyProperties().getExcludeTags());
    }

    /**
     * Sets the exclude tags.
     *
     * @since 4.6
     */
    public void setExcludeTags(Set<String> excludeTags) {
        EagerPropertySupport.setEager(getLazyProperties().getExcludeTags(), excludeTags);
    }
}
