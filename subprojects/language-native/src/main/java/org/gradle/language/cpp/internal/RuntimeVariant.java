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
import org.gradle.api.artifacts.ModuleDependency;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.attributes.Usage;
import org.gradle.api.internal.component.SoftwareComponentInternal;
import org.gradle.api.internal.component.UsageContext;

import java.util.Set;

public class RuntimeVariant implements SoftwareComponentInternal {
    private final String name;
    private final Usage runtimeUsage;
    private final Configuration runtimeElementsConfiguration;

    public RuntimeVariant(String name, Usage runtimeUsage, Configuration runtimeElementsConfiguration) {
        this.name = name;
        this.runtimeUsage = runtimeUsage;
        this.runtimeElementsConfiguration = runtimeElementsConfiguration;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Set<? extends UsageContext> getUsages() {
        return ImmutableSet.of(new UsageContext() {
            @Override
            public Usage getUsage() {
                return runtimeUsage;
            }

            @Override
            public Set<PublishArtifact> getArtifacts() {
                return runtimeElementsConfiguration.getArtifacts();
            }

            @Override
            public Set<ModuleDependency> getDependencies() {
                return ImmutableSet.of();
            }
        });
    }
}
