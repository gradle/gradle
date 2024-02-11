/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.internal.component.model;

import java.util.Set;

/**
 * Represents the Ivy descriptor representation of an exclude.
 * In an Ivy descriptor, and exclude can apply to a number of configurations.
 */
public interface Exclude extends ExcludeMetadata {
    /**
     * The configurations that this exclude will apply to.
     * NOTE: only supported for exclude rules sourced from an Ivy module descriptor (ivy.xml).
     *
     * @return The set of configurations that apply, or an empty set if this exclude applies to _all_ configurations.
     */
    Set<String> getConfigurations();
}
