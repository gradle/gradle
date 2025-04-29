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

import com.google.common.collect.ImmutableList;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.internal.DisplayName;
import org.gradle.internal.component.external.model.ImmutableCapabilities;
import org.jspecify.annotations.Nullable;

/**
 * Would be better named {@code VariantArtifactMetadata}.
 * <p>
 * Describes the artifacts of a {@link VariantGraphResolveMetadata}. Graph variants may have multiple
 * artifact variants, where each artifact variant may have different artifacts, but inherit the dependencies
 * of its graph variant.
 */
public interface VariantResolveMetadata {
    String getName();

    /**
     * An identifier for this artifact variant.
     * <p>
     * May be null for adhoc variants.
     */
    @Nullable
    Identifier getIdentifier();

    DisplayName asDescribable();

    ImmutableAttributes getAttributes();

    ImmutableList<? extends ComponentArtifactMetadata> getArtifacts();

    // TODO: This type should not expose capabilities, as all artifact variants within a single graph variant
    // should have the same capability.
    ImmutableCapabilities getCapabilities();

    boolean isExternalVariant();

    /**
     * Is this variant eligible for caching?
     *
     * Only variants from a project component are eligible for caching.
     *
     * @see <a href="https://github.com/gradle/gradle/pull/23500#discussion_r1073224819">Context</a>
     */
    default boolean isEligibleForCaching() {
        return false;
    }

    /**
     * An opaque identifier for a an artifact variant.
     * <p>
     * Implementations must implement equals and hashCode.
     */
    interface Identifier {
    }
}
