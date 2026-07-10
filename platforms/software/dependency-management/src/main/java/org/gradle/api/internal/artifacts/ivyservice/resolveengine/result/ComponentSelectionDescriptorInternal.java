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
package org.gradle.api.internal.artifacts.ivyservice.resolveengine.result;

import org.gradle.api.Describable;
import org.gradle.api.artifacts.result.ComponentSelectionDescriptor;

public interface ComponentSelectionDescriptorInternal extends ComponentSelectionDescriptor {
    /**
     * Updates the description of this component selection descriptor.
     *
     * @param description a new textual description of the descriptor.
     *
     * @return this descriptor
     */
    ComponentSelectionDescriptorInternal withDescription(Describable description);

    /**
     * Determines if a custom description was provided. This can be used in reporting to determine if additional details should
     * be displayed.
     *
     * @return true if the description is not the default cause description.
     */
    boolean hasCustomDescription();

    /**
     * Updates this component selection descriptor to indicate it is equivalent to a force
     *
     * @return a new descriptor, equivalent to force
     */
    ComponentSelectionDescriptorInternal markAsEquivalentToForce();

    /**
     * Indicates whether the component selection descriptor is equivalent to a forced dependency
     *
     * @return {@code true} if equivalent to force, {@code false} otherwise
     */
    boolean isEquivalentToForce();

    Describable getDescribable();

}
