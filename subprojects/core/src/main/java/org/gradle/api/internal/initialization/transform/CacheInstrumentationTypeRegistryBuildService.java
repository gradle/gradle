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

package org.gradle.api.internal.initialization.transform;

import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.services.BuildService;
import org.gradle.api.services.BuildServiceParameters;
import org.gradle.internal.classpath.types.ExternalPluginsInstrumentationTypeRegistry;
import org.gradle.internal.classpath.types.GradleCoreInstrumentationTypeRegistry;
import org.gradle.internal.classpath.types.InstrumentationTypeRegistry;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

public abstract class CacheInstrumentationTypeRegistryBuildService implements BuildService<CacheInstrumentationTypeRegistryBuildService.Parameters> {

    public interface Parameters extends BuildServiceParameters {
        ConfigurableFileCollection getClassHierarchy();
    }

    private final AtomicReference<InstrumentationTypeRegistry> instrumentingTypeRegistry = new AtomicReference<>();

    public InstrumentationTypeRegistry getInstrumentingTypeRegistry(GradleCoreInstrumentationTypeRegistry gradleCoreInstrumentationTypeRegistry) {
        if (gradleCoreInstrumentationTypeRegistry.isEmpty()) {
            return InstrumentationTypeRegistry.empty();
        }
        if (instrumentingTypeRegistry.get() != null) {
            return instrumentingTypeRegistry.get();
        }
        return newInstrumentationTypeRegistry(gradleCoreInstrumentationTypeRegistry);
    }

    private synchronized InstrumentationTypeRegistry newInstrumentationTypeRegistry(GradleCoreInstrumentationTypeRegistry gradleCoreInstrumentationTypeRegistry) {
        if (instrumentingTypeRegistry.get() != null) {
            return instrumentingTypeRegistry.get();
        }
        Map<String, Set<String>> classHierarchy = readClassHierarchy();
        ExternalPluginsInstrumentationTypeRegistry registry = new ExternalPluginsInstrumentationTypeRegistry(classHierarchy, gradleCoreInstrumentationTypeRegistry);
        instrumentingTypeRegistry.set(registry);
        return registry;
    }

    private Map<String, Set<String>> readClassHierarchy() {
        Set<File> files = getParameters().getClassHierarchy().getFiles();
        Map<String, Set<String>> classHierarchy = new HashMap<>();
        files.forEach(file -> {
            Properties properties = new Properties();
            try (InputStream inputStream = Files.newInputStream(file.toPath())) {
                properties.load(inputStream);
                properties.forEach((key, value) -> {
                    Set<String> keyTypes = classHierarchy.computeIfAbsent((String) key, __ -> new HashSet<>());
                    Collections.addAll(keyTypes, ((String) value).split(","));
                });
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
        return classHierarchy;
    }

    public void clear() {
        // Remove the reference to the registry, so that it can be garbage collected,
        // since build service instance is not deregistered
        instrumentingTypeRegistry.set(null);
    }
}
