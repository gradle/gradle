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

import org.gradle.api.Transformer;
import org.gradle.api.specs.Spec;
import org.gradle.internal.Pair;
import org.gradle.internal.classpath.CachedClasspathTransformer;
import org.gradle.internal.classpath.ClassPath;
import org.gradle.internal.classpath.DefaultClassPath;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.classloader.ClassLoaderSpec;
import org.gradle.internal.classloader.VisitableURLClassLoader;
import org.gradle.util.CollectionUtils;

import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Collection;
import java.util.List;

public class DaemonSidePayloadClassLoaderFactory implements PayloadClassLoaderFactory {
    private final PayloadClassLoaderFactory delegate;
    private final CachedClasspathTransformer cachedClasspathTransformer;

    public DaemonSidePayloadClassLoaderFactory(PayloadClassLoaderFactory delegate, CachedClasspathTransformer cachedClasspathTransformer) {
        this.delegate = delegate;
        this.cachedClasspathTransformer = cachedClasspathTransformer;
    }

    public ClassLoader getClassLoaderFor(ClassLoaderSpec spec, List<? extends ClassLoader> parents) {
        if (spec instanceof VisitableURLClassLoader.Spec) {
            VisitableURLClassLoader.Spec urlSpec = (VisitableURLClassLoader.Spec) spec;
            if (parents.size() != 1) {
                throw new IllegalStateException("Expected exactly one parent ClassLoader");
            }

            // urls on left will be file urls, all others on right
            Pair<Collection<URL>, Collection<URL>> urls = CollectionUtils.partition(urlSpec.getClasspath(), new Spec<URL>() {
                @Override
                public boolean isSatisfiedBy(URL url) {
                    return url.getProtocol().equals("file");
                }
            });
            ClassPath classPath = DefaultClassPath.of(CollectionUtils.collect(urls.left(), new Transformer<File, URL>() {
                @Override
                public File transform(URL url) {
                    try {
                        return new File(url.toURI());
                    } catch (URISyntaxException e) {
                        throw UncheckedException.throwAsUncheckedException(e);
                    }
                }
            }));

            // convert the file urls to cached jar files
            ClassPath cachedClassPath = cachedClasspathTransformer.transform(classPath);

            return new VisitableURLClassLoader(parents.get(0), CollectionUtils.addAll(cachedClassPath.getAsURLs(), urls.right()));
        }
        return delegate.getClassLoaderFor(spec, parents);
    }
}
