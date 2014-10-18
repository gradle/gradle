/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.tooling.internal.provider;

import org.gradle.cache.CacheRepository;
import org.gradle.cache.PersistentCache;
import org.gradle.cache.internal.FileLockManager;
import org.gradle.internal.Factories;
import org.gradle.internal.Factory;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.classloader.ClassLoaderSpec;
import org.gradle.internal.classloader.MutableURLClassLoader;

import java.io.Closeable;
import java.io.File;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import static org.gradle.cache.internal.filelock.LockOptionsBuilder.mode;

public class DaemonSidePayloadClassLoaderFactory implements PayloadClassLoaderFactory, Closeable {
    private final PayloadClassLoaderFactory delegate;
    private final JarCache jarCache;
    private final PersistentCache cache;

    public DaemonSidePayloadClassLoaderFactory(PayloadClassLoaderFactory delegate, JarCache jarCache, CacheRepository cacheRepository) {
        this.delegate = delegate;
        this.jarCache = jarCache;
        cache = cacheRepository
                .cache("jars-1")
                .withDisplayName("jars")
                .withCrossVersionCache()
                .withLockOptions(mode(FileLockManager.LockMode.None))
                .open();
    }

    public void close() {
        cache.close();
    }

    public ClassLoader getClassLoaderFor(ClassLoaderSpec spec, List<? extends ClassLoader> parents) {
        if (spec instanceof MutableURLClassLoader.Spec) {
            MutableURLClassLoader.Spec urlSpec = (MutableURLClassLoader.Spec) spec;
            if (parents.size() != 1) {
                throw new IllegalStateException("Expected exactly one parent ClassLoader");
            }
            List<URL> cachedClassPath = new ArrayList<URL>(urlSpec.getClasspath().size());
            for (URL url : urlSpec.getClasspath()) {
                if (url.getProtocol().equals("file")) {
                    try {
                        final File file = new File(url.toURI());
                        if (file.isFile()) {
                            File cached = cache.useCache("Locate Jar file", new Factory<File>() {
                                public File create() {
                                    return jarCache.getCachedJar(file, Factories.constant(cache.getBaseDir()));
                                }
                            });
                            cachedClassPath.add(cached.toURI().toURL());
                            continue;
                        }
                    } catch (MalformedURLException e) {
                        throw UncheckedException.throwAsUncheckedException(e);
                    } catch (URISyntaxException e) {
                        throw UncheckedException.throwAsUncheckedException(e);
                    }
                }
                cachedClassPath.add(url);
            }

            return new MutableURLClassLoader(parents.get(0), cachedClassPath);
        }
        return delegate.getClassLoaderFor(spec, parents);
    }
}
