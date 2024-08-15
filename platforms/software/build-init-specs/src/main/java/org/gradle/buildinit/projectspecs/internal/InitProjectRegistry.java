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

package org.gradle.buildinit.projectspecs.internal;

import org.gradle.buildinit.projectspecs.InitProjectGenerator;
import org.gradle.buildinit.projectspecs.InitProjectSpec;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * A registry of the available {@link InitProjectSpec}s that can be used to generate new projects via the {@code init} task.
 *
 * @since 8.11
 */
public final class InitProjectRegistry {
    private final Map<Class<? extends InitProjectGenerator>, List<InitProjectSpec>> specsByGenerator;

    public InitProjectRegistry(Map<Class<? extends InitProjectGenerator>, List<InitProjectSpec>> templatesBySource) {
        this.specsByGenerator = templatesBySource;
    }

    public List<InitProjectSpec> getProjectSpecs() {
        return specsByGenerator.values().stream().flatMap(List::stream).collect(Collectors.toList());
    }

    public boolean areProjectSpecsAvailable() {
        return !specsByGenerator.isEmpty();
    }

    /**
     * Returns the {@link InitProjectSpec} in the registry with the given type; throwing
     * an exception if there is not exactly one such spec.
     *
     * @param type the type of the project spec to find
     * @return the project spec with the given type
     */
    public InitProjectSpec getProjectSpec(String type) {
        List<InitProjectSpec> matchingSpecs = specsByGenerator.values().stream()
            .flatMap(List::stream)
            .filter(spec -> spec.getType().equals(type))
            .collect(Collectors.toList());

        switch (matchingSpecs.size()) {
            case 0:
                throw new IllegalStateException("Project spec with type: '" + type + "' was not found!");
            case 1:
                return matchingSpecs.get(0);
            default:
                throw new IllegalStateException("Multiple project specs with type: '" + type + "' were found!");
        }
    }

    /**
     * Returns the {@link InitProjectGenerator} type that can be used to generate a project with the given {@link InitProjectSpec}.
     *
     * @param spec the project spec to find the generator for
     * @return the type of generator that can be used to generate a project with the given spec
     */
    public Class<? extends InitProjectGenerator> getProjectGeneratorClass(InitProjectSpec spec) {
        return specsByGenerator.entrySet().stream()
            .filter(entry -> entry.getValue().contains(spec))
            .findFirst()
            .map(Map.Entry::getKey)
            .orElseThrow(() -> new IllegalStateException("Spec: '" + spec.getDisplayName() + "' was not found!"));
    }
}
