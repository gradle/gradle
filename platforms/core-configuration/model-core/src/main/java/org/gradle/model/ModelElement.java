/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.model;

import org.gradle.api.Incubating;
import org.gradle.api.Named;

/**
 * Represents an element in a model. Elements are arranged in a hierarchy.
 */
@Incubating
public interface ModelElement extends Named {
    /**
     * Returns the name of this element. Each element has a name associated with it, that uniquely identifies the element amongst its siblings.
     * Some element have their name generated or automatically assigned, and for these elements the name may not be human consumable.
     */
    @Override
    String getName();

    /**
     * Returns a human-consumable display name for this element.
     */
    String getDisplayName();
}
