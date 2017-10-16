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

import java.util.Set;

public class NativeRuntimeVariant extends NativeVariant implements SoftwareComponent, ComponentWithVariants {
    public NativeRuntimeVariant(String name, Usage usage, Set<? extends PublishArtifact> artifacts, Configuration dependencies) {
        super(name, usage, artifacts, dependencies);
    }

    public NativeRuntimeVariant(String name, Usage linkUsage, Configuration linkElements, Usage runtimeUsage, Configuration runtimeElements) {
        super(name, linkUsage, linkElements, runtimeUsage, runtimeElements);
    }

    @Override
    public Set<SoftwareComponent> getVariants() {
        return ImmutableSet.of();
    }
}
