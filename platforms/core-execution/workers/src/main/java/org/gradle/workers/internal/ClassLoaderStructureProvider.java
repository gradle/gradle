/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.workers.internal;

import com.google.common.collect.Sets;
import org.gradle.initialization.ClassLoaderRegistry;
import org.gradle.initialization.MixInLegacyTypesClassLoader;
import org.gradle.internal.classloader.ClasspathUtil;
import org.gradle.internal.classloader.FilteringClassLoader;
import org.gradle.internal.classloader.VisitableURLClassLoader;
import org.gradle.internal.classpath.DefaultClassPath;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.Set;

public class ClassLoaderStructureProvider {
    private final ClassLoaderRegistry classLoaderRegistry;

    public ClassLoaderStructureProvider(final ClassLoaderRegistry classLoaderRegistry) {
        this.classLoaderRegistry = classLoaderRegistry;
    }

    public ClassLoaderStructure getWorkerProcessClassLoaderStructure(final Iterable<File> additionalClasspath, Class<?>... classes) {
        MixInLegacyTypesClassLoader.Spec workerExtensionSpec = classLoaderRegistry.getGradleWorkerExtensionSpec();
        FilteringClassLoader.Spec gradleApiFilter = classLoaderRegistry.getGradleApiFilterSpec();
        VisitableURLClassLoader.Spec userSpec = getUserSpec("worker-loader", additionalClasspath, classes);

        // Add the Gradle API filter between the user classloader and the worker infrastructure classloader
        return new HierarchicalClassLoaderStructure(workerExtensionSpec)
                .withChild(gradleApiFilter)
                .withChild(userSpec);
    }

    public ClassLoaderStructure getInProcessClassLoaderStructure(final Iterable<File> additionalClasspath, Class<?>... classes) {
        FilteringClassLoader.Spec gradleApiFilter = classLoaderRegistry.getGradleApiFilterSpec();
        VisitableURLClassLoader.Spec userSpec = getUserSpec("worker-loader", additionalClasspath, classes);
        // Add the Gradle API filter between the user classloader and the worker infrastructure classloader
        return new HierarchicalClassLoaderStructure(gradleApiFilter)
                .withChild(userSpec);
    }

    /**
     * Returns a spec representing the combined "user" classloader for the given classes and additional classpath.  The user classloader assumes it is used as a child of a classloader with the Gradle API.
     */
    public VisitableURLClassLoader.Spec getUserSpec(String name, Iterable<File> additionalClasspath, Class<?>... classes) {
        Set<URL> classpath = Sets.newLinkedHashSet();
        classpath.addAll(DefaultClassPath.of(additionalClasspath).getAsURLs());

        Set<ClassLoader> uniqueClassloaders = Sets.newHashSet();
        for (Class<?> clazz : classes) {
            ClassLoader classLoader = clazz.getClassLoader();
            // System types come from the system classloader and their classloader is null.
            if (classLoader != null) {
                uniqueClassloaders.add(classLoader);
            }
        }
        for (ClassLoader classLoader : uniqueClassloaders) {
            ClasspathUtil.collectClasspathUntil(classLoader, classLoaderRegistry.getGradleApiClassLoader(), classpath);
        }
        return new VisitableURLClassLoader.Spec(name, new ArrayList<>(classpath));
    }
}
