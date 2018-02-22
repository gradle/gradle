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

package org.gradle.api.internal.tasks.testing.junit;

import org.junit.runner.Description;

import java.util.Set;

/**
 * {@link org.junit.runners.Parameterized} runner wraps the original {@link Description}s with some top-level wrapper descriptions (which are named like "[0]", "[1]").
 * These wrapper description should not participate in filtering.
 */
class ParameterizedRunnerCategoryFilter extends CategoryFilter {
    ParameterizedRunnerCategoryFilter(final Set<String> inclusions, final Set<String> exclusions, final ClassLoader applicationClassLoader) {
        super(inclusions, exclusions, applicationClassLoader);
    }

    @Override
    protected boolean shouldRun(final Description description, final Description parent) {
        return parent == null || super.shouldRun(description, parent);
    }
}
