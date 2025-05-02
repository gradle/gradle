/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.api.artifacts.result;

import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.capabilities.Capability;

import java.util.List;
import java.util.Optional;

/**
 * The result of successfully resolving a component variant.
 *
 * @since 3.5
 */
public interface ResolvedVariantResult {
    /**
     * The component which owns this variant.
     *
     * @return the component identifier of this variant
     * @since 6.8
     */
    ComponentIdentifier getOwner();

    /**
     * The attributes associated with this variant.
     */
    AttributeContainer getAttributes();

    /**
     * The display name of this variant, for diagnostics.
     *
     * @since 4.6
     */
    String getDisplayName();

    /**
     * The capabilities provided by this variant
     *
     * @since 5.3
     */
    List<Capability> getCapabilities();

    /**
     * If present, this means that this variant is a bridge to another variant
     * found in another module. This corresponds to variants which are marked
     * as "available-at" in Gradle Module Metadata.
     *
     * @return the external variant, if any
     * @since 6.8
     */
    Optional<ResolvedVariantResult> getExternalVariant();
}
