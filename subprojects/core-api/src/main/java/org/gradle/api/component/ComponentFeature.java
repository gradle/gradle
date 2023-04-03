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

package org.gradle.api.component;

import org.gradle.api.Incubating;
import org.gradle.api.Named;
import org.gradle.api.NamedDomainObjectSet;
import org.gradle.api.capabilities.CapabilitiesMetadata;

import javax.annotation.Nullable;

/**
 * A feature of a component, which encapsulates the logic and domain objects required to
 * implement a single software product exposed by a component. Features are used to model
 * constructs like production libraries, test suites, test fixtures, applications, etc. by
 * exposing variants.
 *
 * <p>Features are classified by their capabilities. Each variant of a feature provides at least
 * the same set of capabilities as the feature itself. Some variants may expose additional
 * capabilities than those of its owning feature, for example with fat jars.</p>
 *
 * @since 8.2
 */
@Incubating
public interface ComponentFeature extends Named {

    /**
     * Returns the description for this feature.
     */
    @Nullable
    String getDescription();

    /**
     * Get the capabilities of this feature. All variants exposed by this feature must provide at least
     * the same capabilities as this feature.
     */
    CapabilitiesMetadata getCommonCapabilities();

    /**
     * Get the variants exposed by this feature. These variants can be subsequently exposed by
     * a component so that they can be consumed by publication or dependency resolution.
     */
    NamedDomainObjectSet<? extends ConsumableVariant> getVariants();

}
