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

package org.gradle.internal.component.local.model;

import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.internal.component.external.model.ImmutableCapabilities;

/**
 * Default implementation of {@link LocalVariantGraphResolveMetadata} used to represent a single local variant.
 */
public final class DefaultLocalVariantGraphResolveMetadata implements LocalVariantGraphResolveMetadata {

    private final String name;
    private final boolean transitive;
    private final ImmutableAttributes attributes;
    private final boolean deprecatedForConsumption;
    private final ImmutableCapabilities capabilities;

    public DefaultLocalVariantGraphResolveMetadata(
        String name,
        boolean transitive,
        ImmutableAttributes attributes,
        ImmutableCapabilities capabilities,
        boolean deprecatedForConsumption
    ) {
        this.name = name;
        this.transitive = transitive;
        this.attributes = attributes;
        this.capabilities = capabilities;
        this.deprecatedForConsumption = deprecatedForConsumption;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getConfigurationName() {
        return name;
    }

    @Override
    public boolean isTransitive() {
        return transitive;
    }

    @Override
    public ImmutableAttributes getAttributes() {
        return attributes;
    }

    @Override
    public boolean isDeprecated() {
        return deprecatedForConsumption;
    }

    @Override
    public ImmutableCapabilities getCapabilities() {
        return capabilities;
    }

    @Override
    public boolean isExternalVariant() {
        return false;
    }

    @Override
    public String toString() {
        return "variant " + name;
    }

}
