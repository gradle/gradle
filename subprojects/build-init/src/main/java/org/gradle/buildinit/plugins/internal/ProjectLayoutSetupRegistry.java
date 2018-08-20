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
import org.gradle.internal.text.TreeFormatter;
import org.gradle.util.CollectionUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class ProjectLayoutSetupRegistry {
    private final Map<String, BuildInitializer> registeredProjectDescriptors = new TreeMap<String, BuildInitializer>();
    private final BuildInitializer defaultType;
    private final BuildConverter converter;

    public ProjectLayoutSetupRegistry(BuildInitializer defaultType, BuildConverter converter) {
        this.defaultType = defaultType;
        this.converter = converter;
        add(defaultType);
        add(converter);
    }

    public void add(BuildInitializer descriptor) {
        if (registeredProjectDescriptors.containsKey(descriptor.getId())) {
            throw new GradleException(String.format("ProjectDescriptor with ID '%s' already registered.", descriptor.getId()));
        }

        registeredProjectDescriptors.put(descriptor.getId(), descriptor);
    }

    // This should turn into a set of converters at some point
    public BuildConverter getBuildConverter() {
        return converter;
    }

    public BuildInitializer getDefault() {
        return defaultType;
    }

    public BuildInitializer get(String type) {
        if (!registeredProjectDescriptors.containsKey(type)) {
            TreeFormatter formatter = new TreeFormatter();
            formatter.node("The requested build setup type '" + type + "' is not supported. Supported types");
            formatter.startChildren();
            for (String candidate : getAllTypes()) {
                formatter.node("'" + candidate + "'");
            }
            formatter.endChildren();
            throw new GradleException(formatter.toString());
        }
        return registeredProjectDescriptors.get(type);
    }

    public List<BuildInitializer> getAll() {
        return CollectionUtils.toList(registeredProjectDescriptors.values());
    }

    public List<String> getBuildGenerators() {
        List<String> result = new ArrayList<String>(registeredProjectDescriptors.size());
        for (BuildInitializer initDescriptor : registeredProjectDescriptors.values()) {
            if (initDescriptor != converter) {
                result.add(initDescriptor.getId());
            }
        }
        return result;
    }

    public List<String> getAllTypes() {
        List<String> result = new ArrayList<String>(registeredProjectDescriptors.size());
        for (BuildInitializer initDescriptor : registeredProjectDescriptors.values()) {
            result.add(initDescriptor.getId());
        }
        return result;
    }
}
