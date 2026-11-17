/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.buildinit.specs.internal;

import org.gradle.buildinit.specs.BuildInitGenerator;
import org.gradle.buildinit.specs.BuildInitSpec;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * An immutable copy of the {@link BuildInitSpec}s held by a {@link BuildInitSpecRegistry} at one point in time.
 * <p>
 * This is a plain value so that the configuration cache can store it with the {@code init} task.
 * The specs and generator types it holds must therefore be serializable by the configuration cache.
 */
public final class BuildInitSpecSnapshot {
    private final Map<Class<? extends BuildInitGenerator>, List<BuildInitSpec>> specsByGeneratorType;

    BuildInitSpecSnapshot(Map<Class<? extends BuildInitGenerator>, List<BuildInitSpec>> specsByGeneratorType) {
        this.specsByGeneratorType = new LinkedHashMap<>();
        specsByGeneratorType.forEach((generatorType, specs) -> this.specsByGeneratorType.put(generatorType, new ArrayList<>(specs)));
    }

    public List<BuildInitSpec> getAllSpecs() {
        return specsByGeneratorType.values().stream().flatMap(List::stream).collect(Collectors.toList());
    }

    public boolean isEmpty() {
        return specsByGeneratorType.isEmpty();
    }

    /**
     * Returns the {@link BuildInitSpec} with the given type; throwing
     * an exception if there is not exactly one such spec.
     *
     * @param type the type of the project spec to find
     * @return the project spec with the given type
     */
    public BuildInitSpec getSpecByType(String type) {
        List<BuildInitSpec> matchingSpecs = getAllSpecs().stream()
            .filter(spec -> Objects.equals(spec.getType(), type))
            .collect(Collectors.toList());

        switch (matchingSpecs.size()) {
            case 0:
                throw new IllegalStateException("Build init spec with type: '" + type + "' was not found!" + System.lineSeparator() +
                    "Known types:" + System.lineSeparator() +
                    getAllSpecs().stream()
                        .map(BuildInitSpec::getType)
                        .map(t -> " - " + t)
                        .collect(Collectors.joining(System.lineSeparator()))
                );
            case 1:
                return matchingSpecs.get(0);
            default:
                throw new IllegalStateException("Multiple project specs: " + matchingSpecs.stream().map(BuildInitSpec::getDisplayName).collect(Collectors.joining(", ")) + " with type: '" + type + "' were found!");
        }
    }

    /**
     * Returns the {@link BuildInitGenerator} type that can be used to generate a project
     * with the given {@link BuildInitSpec}.
     * <p>
     * This searches by project type.
     *
     * @param spec the project spec to find the generator for
     * @return the type of generator that can be used to generate a project with the given spec
     */
    public Class<? extends BuildInitGenerator> getGeneratorForSpec(BuildInitSpec spec) {
        return specsByGeneratorType.entrySet().stream()
            .filter(entry -> entry.getValue().stream().anyMatch(s -> Objects.equals(s.getType(), spec.getType())))
            .findFirst()
            .map(Map.Entry::getKey)
            .orElseThrow(() -> new IllegalStateException("Spec: '" + spec.getDisplayName() + "' with type: '" + spec.getType() + "' is not registered!"));
    }
}
