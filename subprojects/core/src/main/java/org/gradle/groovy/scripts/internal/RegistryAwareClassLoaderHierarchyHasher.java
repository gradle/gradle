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

package org.gradle.groovy.scripts.internal;

import org.gradle.initialization.ClassLoaderRegistry;
import org.gradle.internal.classloader.ConfigurableClassLoaderHierarchyHasher;
import org.gradle.internal.classloader.HashingClassLoaderFactory;
import org.gradle.util.GradleVersion;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

public class RegistryAwareClassLoaderHierarchyHasher extends ConfigurableClassLoaderHierarchyHasher {
    public RegistryAwareClassLoaderHierarchyHasher(ClassLoaderRegistry registry, HashingClassLoaderFactory classLoaderFactory) {
        super(collectKnownClassLoaders(registry), classLoaderFactory);
    }

    private static Map<ClassLoader, String> collectKnownClassLoaders(ClassLoaderRegistry registry) {
        Map<ClassLoader, String> knownClassLoaders = new HashMap<>();

        String gradleVersion = GradleVersion.current().getVersion();

        // Some implementations may use null to represent the bootstrap class loader and in such cases
        // Class.getClassLoader() will return null when the class was loaded by the bootstrap class loader.
        addClassLoader(knownClassLoaders, null, "bootstrap");
        addClassLoader(knownClassLoaders, registry.getRuntimeClassLoader(), "runtime:" + gradleVersion);
        addClassLoader(knownClassLoaders, registry.getGradleApiClassLoader(), "gradle-api:" + gradleVersion);
        addClassLoader(knownClassLoaders, registry.getGradleCoreApiClassLoader(), "gradle-core-api:" + gradleVersion);
        addClassLoader(knownClassLoaders, registry.getPluginsClassLoader(), "plugins:" + gradleVersion);

        return knownClassLoaders;
    }

    private static void addClassLoader(Map<ClassLoader, String> knownClassLoaders, @Nullable ClassLoader classLoader, String id) {
        knownClassLoaders.put(classLoader, id);
    }
}
