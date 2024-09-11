/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.internal.classloader;

import com.google.common.collect.ImmutableList;

import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A {@code ClassLoader} which delegates to multiple parent ClassLoaders.
 *
 * Note: It's usually a good idea to add a {@link CachingClassLoader} between this ClassLoader and any
 * ClassLoaders that use it as a parent, to prevent every path in the ClassLoader graph being searched.
 */
public class MultiParentClassLoader extends ClassLoader implements ClassLoaderHierarchy {

    private final List<ClassLoader> parents;

    static {
        try {
            ClassLoader.registerAsParallelCapable();
        } catch (NoSuchMethodError ignore) {
            // Not supported on Java 6
        }
    }

    public MultiParentClassLoader(ClassLoader... parents) {
        this(Arrays.asList(parents));
    }

    public MultiParentClassLoader(Collection<? extends ClassLoader> parents) {
        super(null);
        this.parents = new CopyOnWriteArrayList<ClassLoader>(parents);
    }

    public void addParent(ClassLoader parent) {
        parents.add(parent);
    }

    public List<ClassLoader> getParents() {
        return ImmutableList.copyOf(parents);
    }

    @Override
    public void visit(ClassLoaderVisitor visitor) {
        visitor.visitSpec(new Spec());
        for (ClassLoader parent : parents) {
            visitor.visitParent(parent);
        }
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        for (ClassLoader parent : parents) {
            try {
                return parent.loadClass(name);
            } catch (ClassNotFoundException e) {
                // Expected
            }
        }
        throw new ClassNotFoundException(String.format("%s not found.", name));
    }

    @SuppressWarnings("deprecation")
    @Override
    protected Package getPackage(String name) {
        for (ClassLoader parent : parents) {
            Package p = ClassLoaderUtils.getPackage(parent, name);
            if (p != null) {
                return p;
            }
        }
        return null;
    }

    @Override
    protected Package[] getPackages() {
        Set<Package> packages = new LinkedHashSet<Package>();
        for (ClassLoader parent : parents) {
            Package[] parentPackages = ClassLoaderUtils.getPackages(parent);
            packages.addAll(Arrays.asList(parentPackages));
        }
        return packages.toArray(new Package[0]);
    }

    @Override
    public URL getResource(String name) {
        for (ClassLoader parent : parents) {
            URL resource = parent.getResource(name);
            if (resource != null) {
                return resource;
            }
        }
        return null;
    }

    @SuppressWarnings("URLEqualsHashCode")
    @Override
    public Enumeration<URL> getResources(String name) throws IOException {
        Set<URL> resources = new LinkedHashSet<URL>();
        for (ClassLoader parent : parents) {
            Enumeration<URL> parentResources = parent.getResources(name);
            while (parentResources.hasMoreElements()) {
                resources.add(parentResources.nextElement());
            }
        }
        return Collections.enumeration(resources);
    }

    public static class Spec extends ClassLoaderSpec {
        @Override
        public boolean equals(Object obj) {
            return obj != null && obj.getClass().equals(Spec.class);
        }

        @Override
        public int hashCode() {
            return getClass().getName().hashCode();
        }
    }
}
