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
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.attributes.Usage;
import org.gradle.api.component.ComponentWithVariants;
import org.gradle.api.component.SoftwareComponent;
import org.gradle.api.internal.artifacts.dependencies.DefaultExternalModuleDependency;
import org.gradle.api.internal.component.SoftwareComponentInternal;
import org.gradle.api.internal.component.UsageContext;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

public class MainLibraryVariant implements ComponentWithVariants, SoftwareComponentInternal {
    private final Set<SoftwareComponent> variants = new HashSet<SoftwareComponent>();
    private final Set<Child> visible = new LinkedHashSet<Child>();
    private final String name;
    private final Usage usage;
    private final Set<? extends PublishArtifact> artifacts;
    private final Configuration dependencies;

    public MainLibraryVariant(String name, Usage usage, Set<? extends PublishArtifact> artifacts, Configuration dependencies) {
        this.name = name;
        this.usage = usage;
        this.artifacts = artifacts;
        this.dependencies = dependencies;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Set<? extends UsageContext> getUsages() {
        Set<UsageContext> usages = new LinkedHashSet<UsageContext>();
        usages.add(new DefaultUsageContext(usage, artifacts, dependencies));
        for (Child child : visible) {
            for (UsageContext usage : child.component.getUsages()) {
                // TODO - should not need this dependency. Needs better support from the dependency resolution engine
                usages.add(new DefaultUsageContext(usage.getUsage(), ImmutableSet.<PublishArtifact>of(), ImmutableSet.of(new DefaultExternalModuleDependency(child.group, child.module, child.version))));
            }
        }
        return usages;
    }

    @Override
    public Set<? extends SoftwareComponent> getVariants() {
        return variants;
    }

    /**
     * Adds a child variant that is visible to consumers.
     * TODO - remove the coordinates. Needs better support from the publishing infrastructure
     */
    public void addVariant(String group, String module, String version, SoftwareComponent variant) {
        variants.add(variant);
        visible.add(new Child(group, module, version, (SoftwareComponentInternal) variant));
    }

    /**
     * Adds a child variant that is not visible to consumers.
     * TODO - remove this. Needs better support for declaring the attributes of a variant so they can be included in the published metadata
     */
    public void addNonVisibleVariant(SoftwareComponent variant) {
        variants.add(variant);
    }

    private static class Child {
        final String group;
        final String module;
        final String version;
        final SoftwareComponentInternal component;

        Child(String group, String module, String version, SoftwareComponentInternal component) {
            this.group = group;
            this.module = module;
            this.version = version;
            this.component = component;
        }
    }
}
