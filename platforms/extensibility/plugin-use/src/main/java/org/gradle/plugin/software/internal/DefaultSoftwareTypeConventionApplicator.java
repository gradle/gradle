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
import org.gradle.internal.Cast;

import java.util.List;

/**
 * Applies the conventions for a given software type to a target project if the provided plugin class is a software type plugin.
 */
public class DefaultSoftwareTypeConventionApplicator implements SoftwareTypeConventionApplicator {
    private final SoftwareTypeRegistry softwareTypeRegistry;
    private final List<SoftwareTypeConventionHandler> conventionHandlers;

    public DefaultSoftwareTypeConventionApplicator(SoftwareTypeRegistry softwareTypeRegistry, List<SoftwareTypeConventionHandler> conventionHandlers) {
        this.softwareTypeRegistry = softwareTypeRegistry;
        this.conventionHandlers = conventionHandlers;
    }

    @Override
    public <T> void applyConventionsTo(T target, Plugin<? super T> plugin) {
        softwareTypeRegistry.implementationFor(Cast.uncheckedCast(plugin.getClass())).ifPresent(softwareTypeImplementation ->
            conventionHandlers.forEach(handler -> handler.apply(target, softwareTypeImplementation.getSoftwareType(), plugin))
        );
    }
}
