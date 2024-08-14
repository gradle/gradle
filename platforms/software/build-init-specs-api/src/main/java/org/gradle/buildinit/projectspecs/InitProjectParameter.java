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

package org.gradle.buildinit.projectspecs;

import org.gradle.api.Incubating;

/**
 * Represents a parameter that can be provided to a {@link InitProjectSpec} to configure it
 * with custom information about this particular project to be generated.
 *
 * @param <T> The type of this parameter
 * @since 8.11
 */
@Incubating
public interface InitProjectParameter<T> {
    /**
     * Returns the name of the parameter.
     *
     * @return the name of the parameter
     * @since 8.11
     */
    String getName();

    /**
     * Returns the type of the parameter.
     *
     * @return the type of the parameter
     * @since 8.11
     */
    T getParameterType();
}
