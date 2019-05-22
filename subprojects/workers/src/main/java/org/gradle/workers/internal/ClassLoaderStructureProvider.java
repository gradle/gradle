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

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import org.gradle.initialization.ClassLoaderRegistry;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.classloader.FilteringClassLoader;
import org.gradle.internal.classloader.VisitableURLClassLoader;
import org.gradle.internal.classpath.DefaultClassPath;

import java.io.File;
import java.util.concurrent.ExecutionException;

public class ClassLoaderStructureProvider {
    private final LoadingCache<Iterable<File>, ClassLoaderStructure> knownClassLoaderStructures;

    public ClassLoaderStructureProvider(final ClassLoaderRegistry classLoaderRegistry) {
        this.knownClassLoaderStructures = CacheBuilder.newBuilder().softValues().build(new CacheLoader<Iterable<File>, ClassLoaderStructure>() {
            @Override
            public ClassLoaderStructure load(Iterable<File> userClasspathFiles) throws Exception {
                FilteringClassLoader.Spec gradleApiFilter = classLoaderRegistry.getGradleApiFilterSpec();
                VisitableURLClassLoader.Spec userSpec = new VisitableURLClassLoader.Spec("worker-loader", DefaultClassPath.of(userClasspathFiles).getAsURLs());
                // Add the Gradle API filter between the user classloader and the worker infrastructure classloader
                return new ClassLoaderStructure(gradleApiFilter).withChild(userSpec);
            }
        });
    }

    public ClassLoaderStructure getDefaultClassLoaderStructure(Iterable<File> userClasspathFiles) {
        try {
            return knownClassLoaderStructures.get(userClasspathFiles);
        } catch (ExecutionException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }
}
