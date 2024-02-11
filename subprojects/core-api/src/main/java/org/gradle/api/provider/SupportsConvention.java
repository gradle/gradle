/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.api.provider;

import org.gradle.api.Incubating;

/**
 * Marks a Gradle API custom type as supporting conventions.
 *
 * <p>
 * <b>Note:</b> This interface is not intended for implementation by build script or plugin authors.
 * </p>
 *
 * @since 8.7
 */
@Incubating
public interface SupportsConvention {
    /**
     * Unsets this object's explicit value, allowing the convention to be
     * selected when evaluating this object's value.
     *
     * @since 8.7
     */
    SupportsConvention unset();

    /**
     * Unsets this object's convention value.
     *
     * @since 8.7
     */
    SupportsConvention unsetConvention();
}
