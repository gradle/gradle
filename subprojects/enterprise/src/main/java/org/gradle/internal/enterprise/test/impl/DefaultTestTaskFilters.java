/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.internal.enterprise.test.impl;

import com.google.common.collect.ImmutableSet;
import org.gradle.internal.enterprise.test.TestTaskFilters;

import java.util.Set;

class DefaultTestTaskFilters implements TestTaskFilters {

    private final Set<String> includePatterns;
    private final Set<String> commandLineIncludePatterns;
    private final Set<String> excludePatterns;
    private final Set<String> includeTags;
    private final Set<String> excludeTags;
    private final Set<String> includeEngines;
    private final Set<String> excludeEngines;

    DefaultTestTaskFilters(
        Set<String> includePatterns,
        Set<String> commandLineIncludePatterns,
        Set<String> excludePatterns,
        Set<String> includeTags,
        Set<String> excludeTags,
        Set<String> includeEngines,
        Set<String> excludeEngines
    ) {
        this.includePatterns = ImmutableSet.copyOf(includePatterns);
        this.commandLineIncludePatterns = ImmutableSet.copyOf(commandLineIncludePatterns);
        this.excludePatterns = ImmutableSet.copyOf(excludePatterns);
        this.includeTags = ImmutableSet.copyOf(includeTags);
        this.excludeTags = ImmutableSet.copyOf(excludeTags);
        this.includeEngines = ImmutableSet.copyOf(includeEngines);
        this.excludeEngines = ImmutableSet.copyOf(excludeEngines);
    }

    @Override
    public Set<String> getIncludePatterns() {
        return includePatterns;
    }

    @Override
    public Set<String> getCommandLineIncludePatterns() {
        return commandLineIncludePatterns;
    }

    @Override
    public Set<String> getExcludePatterns() {
        return excludePatterns;
    }

    @Override
    public Set<String> getIncludeTags() {
        return includeTags;
    }

    @Override
    public Set<String> getExcludeTags() {
        return excludeTags;
    }

    @Override
    public Set<String> getIncludeEngines() {
        return includeEngines;
    }

    @Override
    public Set<String> getExcludeEngines() {
        return excludeEngines;
    }
}
