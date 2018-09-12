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

package org.gradle.api.artifacts;

import org.gradle.api.Incubating;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.internal.HasInternalProtocol;

import javax.annotation.Nullable;

/***
 * Represents a tuple of the component selector of a module and a candidate version
 * to be evaluated in a component selection rule.
 */
@HasInternalProtocol
@Incubating
public interface ComponentSelection {
    /**
     * Gets the candidate version of the module.
     *
     * @return the candidate version of the module
     */
    ModuleComponentIdentifier getCandidate();

    /**
     * Gets the metadata of the component.
     * The metadata may not be available, in which case {@code null} is returned.
     *
     * @return the {@code ComponentMetadata} or {@code null} if not available
     * @since 5.0
     */
    @Nullable
    ComponentMetadata getMetadata();

    /**
     * Used to access a specific descriptor format.
     * For Ivy descriptor, an {@link org.gradle.api.artifacts.ivy.IvyModuleDescriptor ivy module descriptor} is returned.
     *
     * @param descriptorClass the descriptor class
     * @param <T> the descriptor type
     *
     * @return a descriptor fo the requested type, or {@code null} if there was none of the requested type.
     *
     * @see org.gradle.api.artifacts.ivy.IvyModuleDescriptor
     * @since 5.0
     */
    @Nullable
    <T> T getDescriptor(Class<T> descriptorClass);

    /**
     * Rejects the candidate for the resolution.
     *
     * @param reason The reason the candidate was rejected.
     */
    void reject(String reason);
}
