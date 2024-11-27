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
        static Set<String> getIncludePatterns(TestFilter self) {
            return self.getIncludePatterns().get();
        }

        @BytecodeUpgrade
        static TestFilter setIncludePatterns(TestFilter self, String... includePatterns) {
            self.getIncludePatterns().set(Arrays.asList(includePatterns));
            return self;
        }
    }

    static class ExcludePatternsAdapter {
        @BytecodeUpgrade
        static Set<String> getExcludePatterns(TestFilter self) {
            return self.getExcludePatterns().get();
        }

        @BytecodeUpgrade
        static TestFilter setExcludePatterns(TestFilter self, String... excludePatterns) {
            self.getExcludePatterns().set(Arrays.asList(excludePatterns));
            return self;
        }
    }
}
