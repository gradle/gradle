/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.plugin.software.internal;

import com.google.common.collect.ImmutableSet;
import org.gradle.api.internal.plugins.PluginRegistry;
import org.gradle.declarative.dsl.model.annotations.SoftwareType;
import org.gradle.internal.Cast;
import org.gradle.plugin.use.internal.DefaultPluginId;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Set;

public class DefaultSoftwareTypeImplementationRegistry implements SoftwareTypeImplementationRegistry {
    private final SoftwareTypeRegistry softwareTypeRegistry;
    private final PluginRegistry pluginRegistry;
    private final SoftwareTypeImplementationRegistry parent;
    private Set<SoftwareTypeImplementation> softwareTypeImplementations;

    public DefaultSoftwareTypeImplementationRegistry(SoftwareTypeRegistry softwareTypeRegistry, PluginRegistry pluginRegistry, @Nullable SoftwareTypeImplementationRegistry parent) {
        this.softwareTypeRegistry = softwareTypeRegistry;
        this.pluginRegistry = pluginRegistry;
        this.parent = parent;
    }

    private Set<SoftwareTypeImplementation> discoverSoftwareTypeImplementations() {
        if (parent != null) {
            return parent.getSoftwareTypeImplementations();
        } else {
            return softwareTypeRegistry.getRegisteredPluginIds().stream()
                .map(id -> pluginRegistry.lookup(DefaultPluginId.of(id)))
                .flatMap(pluginImplementation ->
                    Arrays.stream(pluginImplementation.asClass().getDeclaredMethods()).flatMap(method ->
                        Arrays.stream(method.getAnnotationsByType(SoftwareType.class))
                            .map(annotation -> new DefaultSoftwareTypeImplementation(
                                annotation.name(),
                                pluginImplementation.getPluginId().getId(),
                                annotation.modelPublicType(),
                                annotation.modelImplementationType(),
                                Cast.uncheckedCast(pluginImplementation.asClass()))
                            )
                    )
                )
                .collect(ImmutableSet.toImmutableSet());
        }
    }

    @Override
    public Set<SoftwareTypeImplementation> getSoftwareTypeImplementations() {
        if (softwareTypeImplementations == null) {
            softwareTypeImplementations = discoverSoftwareTypeImplementations();
        }
        return softwareTypeImplementations;
    }
}
