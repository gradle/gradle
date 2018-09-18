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

import com.google.common.collect.Maps;
import org.gradle.initialization.ClassLoaderRegistry;
import org.gradle.internal.classloader.ClassLoaderHasher;
import org.gradle.internal.classloader.ConfigurableClassLoaderHierarchyHasher;
import org.gradle.util.GradleVersion;

import java.util.Map;

public class RegistryAwareClassLoaderHierarchyHasher extends ConfigurableClassLoaderHierarchyHasher {
    public RegistryAwareClassLoaderHierarchyHasher(ClassLoaderRegistry registry, ClassLoaderHasher classLoaderHasher) {
        super(collectKnownClassLoaders(registry), classLoaderHasher);
    }

    private static Map<ClassLoader, String> collectKnownClassLoaders(ClassLoaderRegistry registry) {
        Map<ClassLoader, String> knownClassLoaders = Maps.newHashMap();

        String javaVmVersion = String.format("%s|%s|%s", System.getProperty("java.vm.name"), System.getProperty("java.vm.vendor"), System.getProperty("java.vm.vendor"));
        ClassLoader systemClassLoader = ClassLoader.getSystemClassLoader();
        if (systemClassLoader != null) {
            addClassLoader(knownClassLoaders, systemClassLoader, "system-app" + javaVmVersion);
            addClassLoader(knownClassLoaders, systemClassLoader.getParent(), "system-ext" + javaVmVersion);
        }

        String gradleVersion = GradleVersion.current().getVersion();
        addClassLoader(knownClassLoaders, registry.getRuntimeClassLoader(), "runtime:" + gradleVersion);
        addClassLoader(knownClassLoaders, registry.getGradleApiClassLoader(), "gradle-api:" + gradleVersion);
        addClassLoader(knownClassLoaders, registry.getGradleCoreApiClassLoader(), "gradle-core-api:" + gradleVersion);
        addClassLoader(knownClassLoaders, registry.getPluginsClassLoader(), "plugins:" + gradleVersion);

        return knownClassLoaders;
    }

    private static void addClassLoader(Map<ClassLoader, String> knownClassLoaders, ClassLoader classLoader, String id) {
        if (classLoader != null) {
            knownClassLoaders.put(classLoader, id);
        }
    }
}
