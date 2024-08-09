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

package org.gradle.buildinit.templates.internal;

import org.gradle.api.logging.Logger;
import org.gradle.buildinit.templates.InitProjectGenerator;
import org.gradle.buildinit.templates.InitProjectSpec;
import org.gradle.buildinit.templates.InitProjectSupplier;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;

public final class TemplateLoader {
    /**
     * We can't load using the current thread here to get the classes loaded by plugins,
     * we have to load from the project's classloader.
     */
    private final ClassLoader classLoader;
    private final Logger logger;

    public TemplateLoader(ClassLoader classLoader, Logger logger) {
        this.classLoader = classLoader;
        this.logger = logger;
    }

    public TemplateRegistry loadTemplates() {
        Map<InitProjectGenerator, List<InitProjectSpec>> templatesBySource = new HashMap<>();

        ServiceLoader.load(InitProjectSupplier.class, classLoader).forEach(supplier -> {
            List<InitProjectSpec> templates = supplier.getProjectDefinitions();
            templates.forEach(template -> logger.info("Loaded template: '{}', supplied by: '{}', generated via: '{}'", template.getDisplayName(), supplier.getClass().getName(), supplier.getProjectGenerator().getClass().getName()));
            templatesBySource.put(supplier.getProjectGenerator(), templates);
        });

        return new TemplateRegistry(templatesBySource);
    }
}
