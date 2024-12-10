/*
 * Copyright 2024 the original author or authors.
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

import org.gradle.internal.instrumentation.api.annotations.BytecodeUpgrade;

import java.util.Arrays;
import java.util.Set;

/**
 * Adapters for {@link TestFilter} properties.
 */
class TestFilterAdapters {
    static class IncludePatternsAdapter {
        @BytecodeUpgrade
        static Set<String> getIncludePatterns(TestFilter testFilter) {
            return testFilter.getIncludePatterns().get();
        }

        @BytecodeUpgrade
        static TestFilter setIncludePatterns(TestFilter testFilter, String... includePatterns) {
            testFilter.getIncludePatterns().set(Arrays.asList(includePatterns));
            return testFilter;
        }
    }

    static class ExcludePatternsAdapter {
        @BytecodeUpgrade
        static Set<String> getExcludePatterns(TestFilter testFilter) {
            return testFilter.getExcludePatterns().get();
        }

        @BytecodeUpgrade
        static TestFilter setExcludePatterns(TestFilter testFilter, String... excludePatterns) {
            testFilter.getExcludePatterns().set(Arrays.asList(excludePatterns));
            return testFilter;
        }
    }
}
