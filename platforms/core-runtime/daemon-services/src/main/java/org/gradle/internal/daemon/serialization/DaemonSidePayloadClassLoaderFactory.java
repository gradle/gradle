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

package org.gradle.internal.daemon.serialization;

import org.gradle.internal.UncheckedException;
import org.gradle.internal.classloader.ClassLoaderSpec;
import org.gradle.internal.classloader.VisitableURLClassLoader;
import org.gradle.internal.classpath.CachedClasspathTransformer;
import org.gradle.tooling.internal.provider.serialization.ClientOwnedClassLoaderSpec;
import org.gradle.tooling.internal.provider.serialization.PayloadClassLoaderFactory;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.gradle.internal.classpath.CachedClasspathTransformer.StandardTransform.None;

public class DaemonSidePayloadClassLoaderFactory implements PayloadClassLoaderFactory {
    private final PayloadClassLoaderFactory delegate;
    private final CachedClasspathTransformer cachedClasspathTransformer;

    public DaemonSidePayloadClassLoaderFactory(PayloadClassLoaderFactory delegate, CachedClasspathTransformer cachedClasspathTransformer) {
        this.delegate = delegate;
        this.cachedClasspathTransformer = cachedClasspathTransformer;
    }

    @Override
    public ClassLoader getClassLoaderFor(ClassLoaderSpec spec, List<? extends ClassLoader> parents) {
        if (spec instanceof ClientOwnedClassLoaderSpec) {
            ClientOwnedClassLoaderSpec clientSpec = (ClientOwnedClassLoaderSpec) spec;
            return createClassLoaderForClassPath("client-owned-daemon-payload-loader", parents, urls(clientSpec.getClasspath()));
        }
        if (spec instanceof VisitableURLClassLoader.Spec) {
            VisitableURLClassLoader.Spec urlSpec = (VisitableURLClassLoader.Spec) spec;
            return createClassLoaderForClassPath(urlSpec.getName() + "-daemon-payload-loader", parents, urlSpec.getClasspath());
        }
        return delegate.getClassLoaderFor(spec, parents);
    }

    private List<URL> urls(List<URI> classpath) {
        List<URL> urls = new ArrayList<>(classpath.size());
        for (URI uri : classpath) {
            try {
                urls.add(uri.toURL());
            } catch (MalformedURLException e) {
                throw UncheckedException.throwAsUncheckedException(e);
            }
        }
        return urls;
    }

    private ClassLoader createClassLoaderForClassPath(String name, List<? extends ClassLoader> parents, List<URL> classpath) {
        if (parents.size() != 1) {
            throw new IllegalStateException("Expected exactly one parent ClassLoader");
        }

        // convert the file urls to cached jar files
        Collection<URL> cachedClassPathUrls = cachedClasspathTransformer.transform(classpath, None);

        return new VisitableURLClassLoader(name, parents.get(0), cachedClassPathUrls);
    }
}
