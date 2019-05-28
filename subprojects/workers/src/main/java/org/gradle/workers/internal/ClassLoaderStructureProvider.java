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

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.gradle.initialization.ClassLoaderRegistry;
import org.gradle.initialization.MixInLegacyTypesClassLoader;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.classloader.FilteringClassLoader;
import org.gradle.internal.classloader.VisitableURLClassLoader;
import org.gradle.internal.classpath.DefaultClassPath;

import java.io.File;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;

public class ClassLoaderStructureProvider {
    private final ClassLoaderRegistry classLoaderRegistry;
    private final Cache<Iterable<File>, ClassLoaderStructure> knownClassLoaderStructures;

    public ClassLoaderStructureProvider(final ClassLoaderRegistry classLoaderRegistry) {
        this.classLoaderRegistry = classLoaderRegistry;
        this.knownClassLoaderStructures = CacheBuilder.newBuilder().softValues().build();
    }

    public ClassLoaderStructure getWorkerProcessClassLoaderStructure(final Iterable<File> userClasspathFiles) {
        try {
            return knownClassLoaderStructures.get(userClasspathFiles, new Callable<ClassLoaderStructure>() {
                @Override
                public ClassLoaderStructure call() throws Exception {
                    MixInLegacyTypesClassLoader.Spec workerExtensionSpec = classLoaderRegistry.getGradleWorkerExtensionSpec();
                    FilteringClassLoader.Spec gradleApiFilter = classLoaderRegistry.getGradleApiFilterSpec();
                    VisitableURLClassLoader.Spec userSpec = new VisitableURLClassLoader.Spec("worker-loader", DefaultClassPath.of(userClasspathFiles).getAsURLs());
                    // Add the Gradle API filter between the user classloader and the worker infrastructure classloader
                    return new HierarchicalClassLoaderStructure(workerExtensionSpec)
                            .withChild(gradleApiFilter)
                            .withChild(userSpec);
                }
            });
        } catch (ExecutionException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    public ClassLoaderStructure getInProcessClassLoaderStructure(final Iterable<File> userClasspathFiles) {
        try {
            return knownClassLoaderStructures.get(userClasspathFiles, new Callable<ClassLoaderStructure>() {
                @Override
                public ClassLoaderStructure call() throws Exception {
                    FilteringClassLoader.Spec gradleApiFilter = classLoaderRegistry.getGradleApiFilterSpec();
                    VisitableURLClassLoader.Spec userSpec = new VisitableURLClassLoader.Spec("worker-loader", DefaultClassPath.of(userClasspathFiles).getAsURLs());
                    // Add the Gradle API filter between the user classloader and the worker infrastructure classloader
                    return new HierarchicalClassLoaderStructure(gradleApiFilter)
                            .withChild(userSpec);
                }
            });
        } catch (ExecutionException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }
}
