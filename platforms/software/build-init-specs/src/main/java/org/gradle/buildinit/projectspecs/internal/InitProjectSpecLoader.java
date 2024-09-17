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

import org.gradle.api.logging.Logger;
import org.gradle.buildinit.projectspecs.InitProjectGenerator;
import org.gradle.buildinit.projectspecs.InitProjectSpec;
import org.gradle.buildinit.projectspecs.InitProjectSource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;

/**
 * Discovers any {@link InitProjectSpec}s contributed by plugins and creates a {@link InitProjectRegistry} containing them
 * that can be used by the {@code init} task as the basis for generating new projects.
 */
public final class InitProjectSpecLoader {
    private final ClassLoader projectPluginClassloader;
    private final Logger logger;

    /**
     * Creates a new loader that will use the given classloader to load templates.
     * <p>
     * We can't just load using the current thread to get the classes loaded by plugins,
     * we have to load from the project's classloader
     *
     * @param projectPluginClassloader the classloader to use to load templates
     * @param logger the logger to use to log messages
     */
    public InitProjectSpecLoader(ClassLoader projectPluginClassloader, Logger logger) {
        this.projectPluginClassloader = projectPluginClassloader;
        this.logger = logger;
    }

    /**
     * Uses a {@link ServiceLoader} to load the {@link InitProjectSpec}s contributed by plugins.
     *
     * @return a registry of project specs
     */
    public InitProjectRegistry loadProjectSpecs() {
        Map<Class<? extends InitProjectGenerator>, List<InitProjectSpec>> specsByGenerator = new HashMap<>();

        ServiceLoader.load(InitProjectSource.class, projectPluginClassloader).forEach(supplier -> {
            List<InitProjectSpec> specs = supplier.getProjectSpecs();
            specs.forEach(template -> logger.info("Loaded project spec: '{} ({})', supplied by: '{}', generated via: '{}'", template.getDisplayName(), template.getType(), supplier.getClass().getName(), supplier.getProjectGenerator().getName()));
            specsByGenerator.computeIfAbsent(supplier.getProjectGenerator(), k -> new ArrayList<>()).addAll(specs);
        });

        return new InitProjectRegistry(specsByGenerator);
    }
}
