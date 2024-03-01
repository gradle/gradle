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

import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.testing.TestFrameworkOptions;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * The JUnit platform specific test options.
 *
 * @see <a href="https://junit.org/junit5/docs/current/user-guide">JUnit 5 User Guide</a>
 * @since 4.6
 */
public class JUnitPlatformOptions extends TestFrameworkOptions {
    private Set<String> includeEngines = new LinkedHashSet<String>();

    private Set<String> excludeEngines = new LinkedHashSet<String>();

    private Set<String> includeTags = new LinkedHashSet<String>();

    private Set<String> excludeTags = new LinkedHashSet<String>();

    /**
     * Copies the options from the source options into the current one.
     * @since 8.0
     */
    public void copyFrom(JUnitPlatformOptions other) {
        replace(this.includeEngines, other.includeEngines);
        replace(this.excludeEngines, other.excludeEngines);
        replace(this.includeTags, other.includeTags);
        replace(this.excludeTags, other.excludeTags);
    }

    private static void replace(Set<String> target, Set<String> source) {
        target.clear();
        target.addAll(source);
    }

    /**
     * The set of engines to run with.
     *
     * @see <a href="https://junit.org/junit5/docs/current/user-guide/#launcher-api-engines-custom">Test Engine</a>
     */
    public JUnitPlatformOptions includeEngines(String... includeEngines) {
        this.includeEngines.addAll(Arrays.asList(includeEngines));
        return this;
    }

    /**
     * The set of tags to run with.
     *
     * @see <a href="https://junit.org/junit5/docs/current/user-guide/#writing-tests-tagging-and-filtering">Tagging and Filtering</a>
     */
    public JUnitPlatformOptions includeTags(String... includeTags) {
        this.includeTags.addAll(Arrays.asList(includeTags));
        return this;
    }

    /**
     * The set of engines to exclude.
     *
     * @see <a href="https://junit.org/junit5/docs/current/user-guide/#launcher-api-engines-custom">Test Engine</a>
     */
    public JUnitPlatformOptions excludeEngines(String... excludeEngines) {
        this.excludeEngines.addAll(Arrays.asList(excludeEngines));
        return this;
    }

    /**
     * The set of tags to exclude.
     *
     * @see <a href="https://junit.org/junit5/docs/current/user-guide/#writing-tests-tagging-and-filtering">Tagging and Filtering</a>
     */
    public JUnitPlatformOptions excludeTags(String... excludeTags) {
        this.excludeTags.addAll(Arrays.asList(excludeTags));
        return this;
    }

    @Input
    public Set<String> getIncludeEngines() {
        return includeEngines;
    }

    @Input
    public Set<String> getIncludeTags() {
        return includeTags;
    }

    public void setIncludeEngines(Set<String> includeEngines) {
        this.includeEngines = includeEngines;
    }

    @Input
    public Set<String> getExcludeEngines() {
        return excludeEngines;
    }

    public void setExcludeEngines(Set<String> excludeEngines) {
        this.excludeEngines = excludeEngines;
    }

    public void setIncludeTags(Set<String> includeTags) {
        this.includeTags = includeTags;
    }

    @Input
    public Set<String> getExcludeTags() {
        return excludeTags;
    }

    public void setExcludeTags(Set<String> excludeTags) {
        this.excludeTags = excludeTags;
    }
}
