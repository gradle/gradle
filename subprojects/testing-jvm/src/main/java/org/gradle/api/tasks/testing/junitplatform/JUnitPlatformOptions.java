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
import org.gradle.api.tasks.testing.TestFrameworkOptions;
import org.gradle.api.tasks.testing.junit.JUnitOptions;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * The JUnit platform specific test options. To be implemented in next step.
 *
 * @since 4.6
 */
@Incubating
public class JUnitPlatformOptions extends TestFrameworkOptions {
    private Set<String> includeEngines = new LinkedHashSet<String>();

    private Set<String> excludeEngines = new LinkedHashSet<String>();

    private Set<String> includeTags = new LinkedHashSet<String>();

    private Set<String> excludeTags = new LinkedHashSet<String>();

    @Incubating
    public JUnitPlatformOptions includeEngines(String... includeEngines) {
        this.includeEngines.addAll(Arrays.asList(includeEngines));
        return this;
    }

    @Incubating
    public JUnitPlatformOptions includeTags(String... includeTags) {
        this.includeTags.addAll(Arrays.asList(includeTags));
        return this;
    }

    @Incubating
    public JUnitPlatformOptions excludeEngines(String... excludeEngines) {
        this.excludeEngines.addAll(Arrays.asList(excludeEngines));
        return this;
    }

    @Incubating
    public JUnitPlatformOptions excludeTags(String... excludeTags) {
        this.excludeTags.addAll(Arrays.asList(excludeTags));
        return this;
    }

    /**
     * The set of engines to run with.
     */
    @Incubating
    public Set<String> getIncludeEngines() {
        return includeEngines;
    }

    /**
     * The set of tags to run with.
     */
    @Incubating
    public Set<String> getIncludeTags() {
        return includeTags;
    }

    /**
     * The set of engines to run with.
     */
    public void setIncludeEngines(Set<String> includeEngines) {
        this.includeEngines = includeEngines;
    }

    /**
     * The set of engines to exclude.
     */
    public Set<String> getExcludeEngines() {
        return excludeEngines;
    }

    /**
     * The set of engines to exclude.
     */
    public void setExcludeEngines(Set<String> excludeEngines) {
        this.excludeEngines = excludeEngines;
    }

    /**
     * The set of tags to run with.
     */
    public void setIncludeTags(Set<String> includeTags) {
        this.includeTags = includeTags;
    }

    /**
     * The set of tags to exclude.
     */
    public Set<String> getExcludeTags() {
        return excludeTags;
    }

    /**
     * The set of tags to exclude.
     */
    public void setExcludeTags(Set<String> excludeTags) {
        this.excludeTags = excludeTags;
    }
}
