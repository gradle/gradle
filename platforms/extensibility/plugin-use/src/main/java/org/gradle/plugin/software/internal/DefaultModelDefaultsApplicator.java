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

import org.gradle.api.Plugin;

import java.util.List;

/**
 * Applies the model defaults for a given software type to a target project if the provided plugin class is a software type plugin.
 */
public class DefaultModelDefaultsApplicator implements ModelDefaultsApplicator {
    private final List<ModelDefaultsHandler> defaultsHandlers;

    public DefaultModelDefaultsApplicator(List<ModelDefaultsHandler> defaultsHandlers) {
        this.defaultsHandlers = defaultsHandlers;
    }

    @Override
    public <T> void applyDefaultsTo(T target, Plugin<?> plugin, SoftwareTypeImplementation<?> softwareTypeImplementation) {
        defaultsHandlers.forEach(handler -> handler.apply(target, softwareTypeImplementation.getSoftwareType(), plugin));
    }
}
