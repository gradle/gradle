/*
 * Copyright 2019 the original author or authors.
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
package org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph;

import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.capabilities.Capability;
import org.gradle.internal.DisplayName;

import java.util.List;

public interface ResolvedVariantDetails {
    /**
     * Returns the name of the resolved variant. This can currently be 2 different things: for legacy components,
     * it's going to be the name of a "configuration" (either a project configuration, an Ivy configuration name or a Maven "scope").
     * For components with variants, it's going to be the name of the variant. This name is going to be used for reporting purposes.
     */
    DisplayName getVariantName();

    /**
     * Returns the attributes of the resolved variant. This is going to be used for reporting purposes. In practice, variant attributes
     * should effectively be what defines the _identity_ of the variant. In practice, because we have multiple kind of components, it's
     * not necessarily the case.
     */
    AttributeContainer getVariantAttributes();

    /**
     * Returns the capabilities provided by this variant.
     */
    List<Capability> getCapabilities();
}
