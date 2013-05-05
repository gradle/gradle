/*
 * Copyright 2011 the original author or authors.
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

package org.gradle.tooling.model;

import org.gradle.api.Nullable;

/**
 * Described model element.
 *
 * @since 1.0-milestone-5
 */
public interface Element extends Model {

    /**
     * Returns the name of the element. Note that the name is not a unique identifier.
     *
     * @return The name of the element.
     * @since 1.0-milestone-5
     */
    String getName();

    /**
     * Returns the description of the element, or {@code null} if it has no description.
     *
     * @return The description of the element, or {@code null} if it has no description.
     * @since 1.0-milestone-5
     */
    @Nullable
    String getDescription();
}
