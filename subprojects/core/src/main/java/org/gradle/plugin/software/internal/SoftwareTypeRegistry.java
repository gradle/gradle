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
import org.gradle.api.Project;

import java.util.Set;

/**
 * Allows registration of software types implemented by plugins.
 */
public interface SoftwareTypeRegistry {
    /**
     * Registers a plugin as providing a software type.  Cannot be called again once the list of software types has been
     * queried via {@link #getSoftwareTypeImplementations()}.
     */
    void register(Class<? extends Plugin<Project>> pluginClass);

    /**
     * Returns a set of the software types available along with their model types and associated plugins.  Note that once
     * method is called, calling {@link #register(Class)} will result in an error.
     */
    Set<SoftwareTypeImplementation<?>> getSoftwareTypeImplementations();

    /**
     * Returns whether a plugin is registered as providing a software type or not.
     */
    boolean isRegistered(Class<? extends Plugin<?>> pluginClass);
}
