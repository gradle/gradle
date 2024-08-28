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

import org.gradle.api.provider.SetProperty;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.testing.TestFrameworkOptions;
import org.gradle.internal.instrumentation.api.annotations.ReplacesEagerProperty;

/**
 * The JUnit platform specific test options.
 *
 * @see <a href="https://junit.org/junit5/docs/current/user-guide">JUnit 5 User Guide</a>
 * @since 4.6
 */
public abstract class JUnitPlatformOptions extends TestFrameworkOptions {

    /**
     * Copies the options from the source options into the current one.
     * @since 8.0
     */
    public void copyFrom(JUnitPlatformOptions other) {
        getIncludeEngines().set(other.getIncludeEngines());
        getExcludeEngines().set(other.getExcludeEngines());
        getIncludeTags().set(other.getIncludeTags());
        getExcludeTags().set(other.getExcludeTags());
    }

    /**
     * The set of engines to run with.
     *
     * @see <a href="https://junit.org/junit5/docs/current/user-guide/#launcher-api-engines-custom">Test Engine</a>
     */
    public JUnitPlatformOptions includeEngines(String... includeEngines) {
        getIncludeEngines().addAll(includeEngines);
        return this;
    }

    /**
     * The set of tags to run with.
     *
     * @see <a href="https://junit.org/junit5/docs/current/user-guide/#writing-tests-tagging-and-filtering">Tagging and Filtering</a>
     */
    public JUnitPlatformOptions includeTags(String... includeTags) {
        getIncludeTags().addAll(includeTags);
        return this;
    }

    /**
     * The set of engines to exclude.
     *
     * @see <a href="https://junit.org/junit5/docs/current/user-guide/#launcher-api-engines-custom">Test Engine</a>
     */
    public JUnitPlatformOptions excludeEngines(String... excludeEngines) {
        getExcludeEngines().addAll(excludeEngines);
        return this;
    }

    /**
     * The set of tags to exclude.
     *
     * @see <a href="https://junit.org/junit5/docs/current/user-guide/#writing-tests-tagging-and-filtering">Tagging and Filtering</a>
     */
    public JUnitPlatformOptions excludeTags(String... excludeTags) {
        getExcludeTags().addAll(excludeTags);
        return this;
    }

    @Input
    @ReplacesEagerProperty
    public abstract SetProperty<String> getIncludeEngines();

    @Input
    @ReplacesEagerProperty
    public abstract SetProperty<String> getIncludeTags();

    @Input
    @ReplacesEagerProperty
    public abstract SetProperty<String> getExcludeEngines();

    @Input
    @ReplacesEagerProperty
    public abstract SetProperty<String> getExcludeTags();
}
