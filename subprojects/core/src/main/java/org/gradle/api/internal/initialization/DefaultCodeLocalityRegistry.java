/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.api.internal.initialization;

import com.google.common.collect.ImmutableSet;
import org.gradle.initialization.ClassLoaderRegistry;
import org.gradle.internal.classloader.ProtectionDomains;
import org.gradle.internal.code.Locality;
import org.jspecify.annotations.Nullable;

import javax.inject.Inject;

import java.io.File;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class DefaultCodeLocalityRegistry implements CodeLocalityRegistry {

    private final Set<ClassLoader> coreClassLoaders;
    private final Map<File, Locality> classpathEntries = new ConcurrentHashMap<>();

    @Inject
    public DefaultCodeLocalityRegistry(ClassLoaderRegistry classLoaderRegistry) {
        this(classLoaderRegistry.getRuntimeClassLoader(), classLoaderRegistry.getPluginsClassLoader());
    }

    DefaultCodeLocalityRegistry(ClassLoader... coreClassLoaders) {
        ImmutableSet.Builder<ClassLoader> loaders = ImmutableSet.builder();
        for (ClassLoader loader : coreClassLoaders) {
            for (ClassLoader current = loader; current != null; current = current.getParent()) {
                loaders.add(current);
            }
        }
        this.coreClassLoaders = loaders.build();
    }

    @Override
    public void recordClasspathEntry(File file, Locality locality) {
        classpathEntries.put(file.getAbsoluteFile(), locality);
    }

    @Override
    public Locality localityOf(Class<?> clazz) {
        if (coreClassLoaders.contains(clazz.getClassLoader())) {
            return Locality.CORE;
        }
        File codeSource = codeSourceOf(clazz);
        if (codeSource == null) {
            return Locality.UNKNOWN;
        }
        Locality result = classpathEntries.getOrDefault(codeSource.getAbsoluteFile(), Locality.UNKNOWN);
        if (result == Locality.UNKNOWN) {
            System.out.println("LOCALITY-DEBUG miss for " + clazz.getName() + " loaded from " + codeSource + " by " + clazz.getClassLoader() + "; recorded: " + classpathEntries);
        }
        return result;
    }

    @Nullable
    private static File codeSourceOf(Class<?> clazz) {
        try {
            return ProtectionDomains.codeSourceFileOf(clazz.getProtectionDomain());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
