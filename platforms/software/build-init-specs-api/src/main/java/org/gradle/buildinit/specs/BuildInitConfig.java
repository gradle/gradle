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

package org.gradle.buildinit.specs;

import org.gradle.api.Incubating;

import java.util.Map;

/**
 * Represents a {@link BuildInitSpec} that has been configured by the user, and can provide arguments
 * for all of its {@link BuildInitParameter}s.
 *
 * @implSpec Implementations must be immutable.
 * @since 8.12
 */
@Incubating
public interface BuildInitConfig {
    /**
     * Returns the build specification this configuration is meant to generate.
     *
     * @return the build specification
     * @since 8.12
     */
    BuildInitSpec getBuildSpec();

    /**
     * Returns the configured parameters for the build specification this instance configures.
     *
     * @return the parameters that have been configured for the spec
     * @since 8.12
     */
    Map<BuildInitParameter<?>, Object> getArguments();
}
