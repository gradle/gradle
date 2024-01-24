/*
 * Copyright 2016 the original author or authors.
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
package org.gradle.buildinit.plugins.internal;

import org.gradle.api.GradleException;
import org.gradle.buildinit.plugins.internal.modifiers.ComponentType;
import org.gradle.buildinit.plugins.internal.modifiers.Language;
import org.gradle.internal.logging.text.TreeFormatter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class ProjectLayoutSetupRegistry {
    private final Map<String, BuildInitializer> registeredProjectDescriptors = new TreeMap<>();
    private final BuildGenerator defaultType;
    private final BuildConverter converter;
    private final TemplateOperationFactory templateOperationFactory;

    public ProjectLayoutSetupRegistry(BuildGenerator defaultType, BuildConverter converter, TemplateOperationFactory templateOperationFactory) {
        this.defaultType = defaultType;
        this.converter = converter;
        this.templateOperationFactory = templateOperationFactory;
        add(defaultType);
        add(converter);
    }

    public void add(BuildInitializer descriptor) {
        if (registeredProjectDescriptors.containsKey(descriptor.getId())) {
            throw new GradleException(String.format("ProjectDescriptor with ID '%s' already registered.", descriptor.getId()));
        }

        registeredProjectDescriptors.put(descriptor.getId(), descriptor);
    }

    // Currently used by `build-logic/build-init-samples/src/main/kotlin/gradlebuild/samples/SamplesGenerator.kt`
    @SuppressWarnings("unused")
    public TemplateOperationFactory getTemplateOperationFactory() {
        return templateOperationFactory;
    }

    // Currently used by `build-logic/build-init-samples/src/main/kotlin/gradlebuild/samples/SamplesGenerator.kt`
    @SuppressWarnings("unused")
    public List<Language> getLanguagesFor(ComponentType componentType) {
        List<BuildGenerator> generators = getGeneratorsFor(componentType);

        List<Language> result = new ArrayList<>();
        for (Language language : Language.values()) {
            for (BuildGenerator generator : generators) {
                if (generator.productionCodeUses(language)) {
                    result.add(language);
                    break;
                }
            }
        }
        return result;
    }

    /**
     * Returns the component types, in display order.
     */
    public List<ComponentType> getComponentTypes() {
        return Arrays.asList(ComponentType.values());
    }

    /**
     * Returns the default component type to use for interactive initialization.
     */
    public ComponentType getDefaultComponentType() {
        return ComponentType.APPLICATION;
    }

    // This should turn into a set of converters at some point
    public BuildConverter getBuildConverter() {
        return converter;
    }

    public BuildGenerator getDefault() {
        return defaultType;
    }

    /**
     * Locates the {@link BuildInitializer} with the given type.
     */
    public BuildInitializer get(String type) {
        if (!registeredProjectDescriptors.containsKey(type)) {
            TreeFormatter formatter = new TreeFormatter();
            formatter.node("The requested build type '" + type + "' is not supported. Supported types");
            formatter.startChildren();
            for (String candidate : getAllTypes()) {
                formatter.node("'" + candidate + "'");
            }
            formatter.endChildren();
            throw new GradleException(formatter.toString());
        }
        return registeredProjectDescriptors.get(type);
    }

    private List<BuildGenerator> getGenerators() {
        List<BuildGenerator> result = new ArrayList<>(registeredProjectDescriptors.size());
        for (BuildInitializer initializer : registeredProjectDescriptors.values()) {
            if (initializer instanceof BuildGenerator) {
                result.add((BuildGenerator) initializer);
            }
        }
        return result;
    }

    public List<BuildGenerator> getGeneratorsFor(ComponentType componentType) {
        List<BuildGenerator> generators = getGenerators();
        List<BuildGenerator> result = new ArrayList<>(generators.size());
        for (BuildGenerator generator : generators) {
            if (generator.getComponentType().equals(componentType)) {
                result.add(generator);
            }
        }
        return result;
    }

    public List<String> getAllTypes() {
        List<String> result = new ArrayList<>(registeredProjectDescriptors.size());
        for (BuildInitializer initDescriptor : registeredProjectDescriptors.values()) {
            result.add(initDescriptor.getId());
        }
        return result;
    }
}
