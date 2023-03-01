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

package org.gradle.language.cpp.internal;

import com.google.common.collect.ImmutableSet;
import org.gradle.api.DomainObjectSet;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.component.ComponentWithVariants;
import org.gradle.api.component.SoftwareComponent;
import org.gradle.api.internal.component.SoftwareComponentInternal;
import org.gradle.api.internal.component.UsageContext;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.internal.ConfigurationSoftwareComponentVariant;

import java.util.LinkedHashSet;
import java.util.Set;

public class MainLibraryVariant implements ComponentWithVariants, SoftwareComponentInternal {
    private final String name;
    private final Set<PublishArtifact> artifacts = new LinkedHashSet<PublishArtifact>();
    private final Configuration dependencies;
    private final DomainObjectSet<SoftwareComponent> variants;
    private final AttributeContainer attributeContainer;

    public MainLibraryVariant(String name, Configuration dependencies, AttributeContainer attributeContainer, ObjectFactory objectFactory) {
        this.name = name;
        this.dependencies = dependencies;
        this.attributeContainer = attributeContainer;
        this.variants = objectFactory.domainObjectSet(SoftwareComponent.class);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Set<? extends UsageContext> getUsages() {
        return ImmutableSet.of(new ConfigurationSoftwareComponentVariant(name, attributeContainer, artifacts, dependencies));
    }

    @Override
    public Set<? extends SoftwareComponent> getVariants() {
        return variants;
    }

    public void addArtifact(PublishArtifact artifact) {
        artifacts.add(artifact);
    }

    /**
     * Adds a child variant
     */
    public void addVariant(SoftwareComponent variant) {
        variants.add(variant);
    }


}
