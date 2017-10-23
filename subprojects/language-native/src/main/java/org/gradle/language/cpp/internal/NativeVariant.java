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
import org.gradle.api.internal.component.SoftwareComponentInternal;
import org.gradle.api.internal.component.UsageContext;

import java.util.Set;

public class NativeVariant implements SoftwareComponentInternal, ComponentWithVariants {
    private final String name;
    private final Usage linkUsage;
    private final Configuration linkElements;
    private final Usage runtimeUsage;
    private final Set<? extends PublishArtifact> runtimeArtifacts;
    private final Configuration runtimeElementsConfiguration;

    public NativeVariant(String name, Usage usage, Set<? extends PublishArtifact> artifacts, Configuration dependencies) {
        this.name = name;
        this.linkUsage = null;
        this.linkElements = null;
        this.runtimeUsage = usage;
        this.runtimeArtifacts = artifacts;
        this.runtimeElementsConfiguration = dependencies;
    }

    public NativeVariant(String name, Usage linkUsage, Configuration linkElements, Usage runtimeUsage, Configuration runtimeElements) {
        this.name = name;
        this.linkUsage = linkUsage;
        this.linkElements = linkElements;
        this.runtimeUsage = runtimeUsage;
        this.runtimeArtifacts = runtimeElements.getAllArtifacts();
        this.runtimeElementsConfiguration = runtimeElements;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Set<SoftwareComponent> getVariants() {
        return ImmutableSet.of();
    }

    @Override
    public Set<? extends UsageContext> getUsages() {
        if (linkElements == null) {
            return ImmutableSet.of(new DefaultUsageContext(name + "-runtime", runtimeUsage, runtimeArtifacts, runtimeElementsConfiguration));
        } else {
            return ImmutableSet.of(new DefaultUsageContext(name + "-link", linkUsage, linkElements.getAllArtifacts(), linkElements), new DefaultUsageContext(name + "-runtime", runtimeUsage, runtimeArtifacts, runtimeElementsConfiguration));
        }
    }
}
