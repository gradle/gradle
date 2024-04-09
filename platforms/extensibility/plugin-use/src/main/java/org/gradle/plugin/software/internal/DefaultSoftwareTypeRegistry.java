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
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.internal.plugins.software.SoftwareType;
import org.gradle.internal.Cast;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Default implementation of {@link SoftwareTypeRegistry} that registers software types.
 */
public class DefaultSoftwareTypeRegistry implements SoftwareTypeRegistry {
    private final List<Class<? extends Plugin<Project>>> pluginClasses = new ArrayList<>();
    private Set<SoftwareTypeImplementation> softwareTypeImplementations;

    @Override
    public void register(Class<? extends Plugin<Project>> pluginClass) {
        if (softwareTypeImplementations != null) {
            throw new IllegalStateException("Cannot register a plugin after software types have been discovered");
        }
        pluginClasses.add(pluginClass);
    }

    private Set<SoftwareTypeImplementation> discoverSoftwareTypeImplementations() {
        Map<String, Class<? extends Plugin<Project>>> registeredTypes = new HashMap<>();
        ImmutableSet.Builder<SoftwareTypeImplementation> softwareTypeImplementations = ImmutableSet.builder();
        pluginClasses.forEach(pluginClass -> {
            Arrays.stream(pluginClass.getDeclaredMethods()).filter(method -> method.isAnnotationPresent(SoftwareType.class)).forEach(method -> {
                SoftwareType softwareType = method.getAnnotation(SoftwareType.class);
                if (registeredTypes.get(softwareType.name()) != pluginClass) {
                    if (registeredTypes.containsKey(softwareType.name())) {
                        throw new IllegalArgumentException("Software type '" + softwareType.name() + "' is registered by both '" + pluginClass.getName() + "' and '" + registeredTypes.get(softwareType.name()).getName() + "'");
                    }
                    registeredTypes.put(softwareType.name(), pluginClass);
                    softwareTypeImplementations.add(
                        new DefaultSoftwareTypeImplementation(
                            softwareType.name(),
                            softwareType.modelPublicType(),
                            Cast.uncheckedNonnullCast(pluginClass)
                        )
                    );
                }
            });

        });
        return softwareTypeImplementations.build();
    }

    @Override
    public Set<SoftwareTypeImplementation> getSoftwareTypeImplementations() {
        if (softwareTypeImplementations == null) {
            softwareTypeImplementations = discoverSoftwareTypeImplementations();
        }
        return softwareTypeImplementations;
    }
}
