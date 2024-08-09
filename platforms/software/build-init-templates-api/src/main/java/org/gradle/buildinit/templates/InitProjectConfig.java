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

package org.gradle.buildinit.templates;

import org.gradle.api.Incubating;

import java.util.Map;

/**
 * Represents a template that has been configured with arguments for all its {@link InitProjectParameter}s.
 *
 * @implSpec Implementations must be immutable.
 * @since 8.11
 */
@Incubating
public interface InitProjectConfig {
    /**
     * Returns the project type of the template.
     *
     * @return the project type of the template
     * @since 8.11
     */
    InitProjectSpec getProjectType();

    /**
     * Returns the configured parameters for the template.
     *
     * @return the parameters that have been configured for the template
     * @since 8.11
     */
    Map<InitProjectParameter<?>, Object> getArguments();
}
