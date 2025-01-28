/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.tooling.internal.provider.serialization;

import com.google.common.collect.ImmutableList;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.classloader.ClassLoaderSpec;
import org.gradle.internal.classloader.ClassLoaderVisitor;
import org.gradle.internal.classloader.SystemClassLoaderSpec;
import org.gradle.internal.classloader.VisitableURLClassLoader;
import org.gradle.util.internal.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.concurrent.ThreadSafe;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * A {@link PayloadClassLoaderRegistry} that maps classes loaded by a set of ClassLoaders that it manages. For ClassLoaders owned by this JVM, inspects the ClassLoader to determine a ClassLoader spec to send across to the peer JVM. For classes serialized from the peer, maintains a set of cached ClassLoaders created using the ClassLoader specs received from the peer.
 */
@ThreadSafe
public class DefaultPayloadClassLoaderRegistry implements PayloadClassLoaderRegistry {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultPayloadClassLoaderRegistry.class);
    private final PayloadClassLoaderFactory classLoaderFactory;
    private final ClassLoaderCache cache;
    private final ClassLoaderToDetailsTransformer detailsToClassLoader = new ClassLoaderToDetailsTransformer();
    private final DetailsToClassLoaderTransformer classLoaderToDetails = new DetailsToClassLoaderTransformer();

    public DefaultPayloadClassLoaderRegistry(ClassLoaderCache cache, PayloadClassLoaderFactory payloadClassLoaderFactory) {
        this.cache = cache;
        this.classLoaderFactory = payloadClassLoaderFactory;
    }

    @Override
    public SerializeMap newSerializeSession() {
        return new SerializeMap() {
            final Map<ClassLoader, Short> classLoaderIds = new HashMap<ClassLoader, Short>();
            final Map<Short, ClassLoaderDetails> classLoaderDetails = new HashMap<Short, ClassLoaderDetails>();

            @Override
            public short visitClass(Class<?> target) {
                ClassLoader classLoader = target.getClassLoader();
                Short id = classLoaderIds.get(classLoader);
                if (id != null) {
                    return id;
                }
                if (classLoaderIds.size() == Short.MAX_VALUE) {
                    throw new UnsupportedOperationException();
                }
                ClassLoaderDetails details = getDetails(classLoader);
                id = (short) (classLoaderIds.size() + 1);

                classLoaderIds.put(classLoader, id);
                classLoaderDetails.put(id, details);

                return id;
            }

            @Override
            public void collectClassLoaderDefinitions(Map<Short, ClassLoaderDetails> details) {
                details.putAll(classLoaderDetails);
            }
        };
    }

    @Override
    public DeserializeMap newDeserializeSession() {
        return new DeserializeMap() {
            @Override
            public Class<?> resolveClass(ClassLoaderDetails classLoaderDetails, String className) throws ClassNotFoundException {
                ClassLoader classLoader = getClassLoader(classLoaderDetails);
                return Class.forName(className, false, classLoader);
            }
        };
    }

    private ClassLoader getClassLoader(ClassLoaderDetails details) {
        ClassLoader classLoader = cache.getClassLoader(details, detailsToClassLoader);
        // A single classloader is used in the daemon for a given set of client owned classloaders
        // When this classloader is reused for multiple requests, the classpath of subsequent requests may be different.
        // So, update the classpath of this shared classloader
        // It would be better to not combine client classloaders but instead to recreate the client side structure
        if (details.spec instanceof ClientOwnedClassLoaderSpec) {
            ClientOwnedClassLoaderSpec spec = (ClientOwnedClassLoaderSpec) details.spec;
            VisitableURLClassLoader urlClassLoader = (VisitableURLClassLoader) classLoader;
            try {
                Set<URI> currentClassPath = uris(urlClassLoader);
                for (URI uri : spec.getClasspath()) {
                    if (!currentClassPath.contains(uri)) {
                        urlClassLoader.addURL(uri.toURL());
                    }
                }
            } catch (URISyntaxException e) {
                throw UncheckedException.throwAsUncheckedException(e);
            } catch (MalformedURLException e) {
                throw UncheckedException.throwAsUncheckedException(e);
            }
        }
        return classLoader;
    }

    private static Set<URI> uris(VisitableURLClassLoader classLoader) throws URISyntaxException {
        URL[] urls = classLoader.getURLs();
        Set<URI> uris = new HashSet<URI>(urls.length);
        for (URL url : urls) {
            uris.add(url.toURI());
        }
        return uris;
    }

    private ClassLoaderDetails getDetails(ClassLoader classLoader) {
        return cache.getDetails(classLoader, classLoaderToDetails);
    }

    private static class ClassLoaderSpecVisitor extends ClassLoaderVisitor {
        final ClassLoader classLoader;
        final List<ClassLoader> parents = new ArrayList<ClassLoader>();
        ClassLoaderSpec spec;
        URL[] classPath;

        public ClassLoaderSpecVisitor(ClassLoader classLoader) {
            this.classLoader = classLoader;
        }

        @Override
        public void visit(ClassLoader candidate) {
            if (candidate == classLoader) {
                super.visit(candidate);
            } else {
                parents.add(candidate);
            }
        }

        @Override
        public void visitClassPath(URL[] classPath) {
            this.classPath = classPath;
        }

        @Override
        public void visitSpec(ClassLoaderSpec spec) {
            this.spec = spec;
        }
    }

    private class ClassLoaderToDetailsTransformer implements ClassLoaderCache.Transformer<ClassLoader, ClassLoaderDetails> {
        @Override
        public ClassLoader transform(ClassLoaderDetails details) {
            List<ClassLoader> parents = new ArrayList<ClassLoader>();
            for (ClassLoaderDetails parentDetails : details.parents) {
                parents.add(getClassLoader(parentDetails));
            }
            if (parents.isEmpty()) {
                parents.add(classLoaderFactory.getClassLoaderFor(SystemClassLoaderSpec.INSTANCE, ImmutableList.<ClassLoader>of()));
            }

            LOGGER.info("Creating ClassLoader {} from {} and {}.", details.uuid, details.spec, parents);

            return classLoaderFactory.getClassLoaderFor(details.spec, parents);
        }
    }

    private class DetailsToClassLoaderTransformer implements ClassLoaderCache.Transformer<ClassLoaderDetails, ClassLoader> {
        @Override
        public ClassLoaderDetails transform(ClassLoader classLoader) {
            ClassLoaderSpecVisitor visitor = new ClassLoaderSpecVisitor(classLoader);
            visitor.visit(classLoader);

            if (visitor.spec == null) {
                if (visitor.classPath == null) {
                    visitor.spec = SystemClassLoaderSpec.INSTANCE;
                } else {
                    visitor.spec = new VisitableURLClassLoader.Spec("unknown-loader", CollectionUtils.toList(visitor.classPath));
                }
            }

            UUID uuid = UUID.randomUUID();
            ClassLoaderDetails details = new ClassLoaderDetails(uuid, visitor.spec);
            for (ClassLoader parent : visitor.parents) {
                details.parents.add(getDetails(parent));
            }
            return details;
        }
    }
}
