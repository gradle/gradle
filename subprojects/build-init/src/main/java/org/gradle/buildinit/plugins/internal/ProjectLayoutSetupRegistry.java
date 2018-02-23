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
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.util.CollectionUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ProjectLayoutSetupRegistry {
    private final static Logger LOGGER = Logging.getLogger(ProjectLayoutSetupRegistry.class);
    private final Map<String, ProjectInitDescriptor> registeredProjectDescriptors = new HashMap<String, ProjectInitDescriptor>();

    public void add(final String descriptorID, ProjectInitDescriptor descriptor) {
        if (registeredProjectDescriptors.containsKey(descriptorID)) {
            throw new GradleException(String.format("ProjectDescriptor with ID '%s' already registered.", descriptorID));
        }

        registeredProjectDescriptors.put(descriptorID, descriptor);
        LOGGER.debug("registered setupDescriptor {}", descriptorID);
    }

    public ProjectInitDescriptor get(String type) {
        return registeredProjectDescriptors.get(type);
    }

    public List<ProjectInitDescriptor> getAll() {
        return CollectionUtils.toList(registeredProjectDescriptors.values());
    }

    public List<String> getSupportedTypes() {
        return CollectionUtils.sort(registeredProjectDescriptors.keySet());
    }

    public boolean supports(String type) {
        return get(type) != null;
    }

}
