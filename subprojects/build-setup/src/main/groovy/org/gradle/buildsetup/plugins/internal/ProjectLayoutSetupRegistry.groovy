/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.buildsetup.plugins.internal

import org.gradle.api.GradleException
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging

class ProjectLayoutSetupRegistry {

    private final Logger logger = Logging.getLogger(ProjectLayoutSetupRegistry.class);
    private final Map<String, ProjectSetupDescriptor> registeredProjectDescriptors = new HashMap<String, ProjectSetupDescriptor>();

    void add(ProjectSetupDescriptor descriptor) {
        if (registeredProjectDescriptors.containsKey(descriptor.id)) {
            throw new GradleException("ProjectDescriptor with ID '${descriptor.id}' already registered.")
        }
        registeredProjectDescriptors.put(descriptor.id, descriptor)
        logger.debug("registered setupDescriptor {}", descriptor.id)
    }

    ProjectSetupDescriptor get(String type) {
        return registeredProjectDescriptors.get(type)
    }

    List<ProjectSetupDescriptor> getAll() {
        return Arrays.asList(registeredProjectDescriptors.values())
    }

    boolean supports(String type) {
        return get(type) != null
    }
}
