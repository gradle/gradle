/*
 * Copyright 2026 the original author or authors.
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
import org.gradle.api.provider.SetProperty;
import org.jspecify.annotations.NullMarked;

/**
 * The lazy properties behind the eager getters and setters of {@link JUnitPlatformOptions}.
 *
 * @since 10.0.0
 */
@Incubating
@NullMarked
public interface JUnitPlatformOptionsInputs {

    /**
     * The engines to run with.
     *
     * @see JUnitPlatformOptions#getIncludeEngines()
     * @since 10.0.0
     */
    SetProperty<String> getIncludeEngines();

    /**
     * The engines to exclude.
     *
     * @see JUnitPlatformOptions#getExcludeEngines()
     * @since 10.0.0
     */
    SetProperty<String> getExcludeEngines();

    /**
     * The tags to run with.
     *
     * @see JUnitPlatformOptions#getIncludeTags()
     * @since 10.0.0
     */
    SetProperty<String> getIncludeTags();

    /**
     * The tags to exclude.
     *
     * @see JUnitPlatformOptions#getExcludeTags()
     * @since 10.0.0
     */
    SetProperty<String> getExcludeTags();
}
