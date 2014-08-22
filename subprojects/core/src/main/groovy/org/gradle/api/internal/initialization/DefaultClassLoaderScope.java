/*
 * Copyright 2013 the original author or authors.
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

import org.gradle.internal.Factory;
import org.gradle.internal.classloader.CachingClassLoader;
import org.gradle.internal.classloader.MultiParentClassLoader;
import org.gradle.internal.classpath.ClassPath;

import java.util.*;

import static com.google.common.collect.Lists.newLinkedList;
import static org.gradle.util.CollectionUtils.unpack;

public class DefaultClassLoaderScope implements ClassLoaderScope {

    public static final String STRICT_MODE_PROPERTY = "org.gradle.classloaderscope.strict";

    private final ClassLoaderScope parent;
    private final ClassLoaderCache classLoaderCache;

    private boolean locked;

    private List<Factory<? extends ClassLoader>> export = new LinkedList<Factory<? extends ClassLoader>>();
    private List<Factory<? extends ClassLoader>> local = new LinkedList<Factory<? extends ClassLoader>>();

    // If these are not null, we are pessimistic (loaders asked for before locking)
    private MultiParentClassLoader exportingClassLoader;
    private MultiParentClassLoader localClassLoader;

    // What is actually exposed
    private ClassLoader effectiveLocalClassLoader;
    private ClassLoader effectiveExportClassLoader;

    public DefaultClassLoaderScope(ClassLoaderScope parent, ClassLoaderCache classLoaderCache) {
        this.parent = parent;
        this.classLoaderCache = classLoaderCache;
    }

    private ClassLoader buildLockedLoader(ClassLoader parent, List<? extends Factory<? extends ClassLoader>> rest) {
        if (rest.size() == 1) {
            ClassLoader loneLoader = rest.get(0).create();
            if (isOrHasParent(loneLoader, parent)) {
                return loneLoader;
            } else {
                return new CachingClassLoader(buildOpenLoader(parent, Collections.singleton(loneLoader)));
            }
        }

        return new CachingClassLoader(buildOpenLoader(parent, newLinkedList(unpack(rest))));
    }

    private MultiParentClassLoader buildOpenLoader(ClassLoader parent, Collection<? extends ClassLoader> loaders) {
        for (ClassLoader loader : loaders) {
            if (isOrHasParent(loader, parent)) {
                return new MultiParentClassLoader(loaders);
            }
        }

        List<ClassLoader> parents = new ArrayList<ClassLoader>(loaders.size() + 1);
        parents.add(parent);
        for (ClassLoader loader : loaders) {
            parents.add(loader);
        }
        return new MultiParentClassLoader(parents);
    }

    private boolean isOrHasParent(ClassLoader search, ClassLoader target) {
        if (search.equals(target)) {
            return true;
        } else {
            ClassLoader parent = search.getParent();
            return parent != null && isOrHasParent(parent, target);
        }
    }

    private void buildEffectiveLoaders() {
        if (effectiveLocalClassLoader == null) {
            if (locked) {
                if (local.isEmpty() && export.isEmpty()) {
                    effectiveLocalClassLoader = parent.getExportClassLoader();
                    effectiveExportClassLoader = parent.getExportClassLoader();
                } else if (export.isEmpty()) {
                    effectiveLocalClassLoader = buildLockedLoader(parent.getExportClassLoader(), local);
                    effectiveExportClassLoader = parent.getExportClassLoader();
                } else if (local.isEmpty()) {
                    effectiveLocalClassLoader = buildLockedLoader(parent.getExportClassLoader(), export);
                    effectiveExportClassLoader = effectiveLocalClassLoader;
                } else {
                    effectiveExportClassLoader = buildLockedLoader(parent.getExportClassLoader(), export);
                    effectiveLocalClassLoader = buildLockedLoader(effectiveExportClassLoader, local);
                }
            } else { // creating before locking, have to create the most flexible setup
                if (Boolean.getBoolean(STRICT_MODE_PROPERTY)) {
                    throw new IllegalStateException("Attempt to define scope class loader before scope is locked");
                }

                exportingClassLoader = buildOpenLoader(parent.getExportClassLoader(), newLinkedList(unpack(export)));
                effectiveExportClassLoader = new CachingClassLoader(exportingClassLoader);

                localClassLoader = buildOpenLoader(effectiveExportClassLoader, newLinkedList(unpack(local)));
                effectiveLocalClassLoader = new CachingClassLoader(localClassLoader);
            }

            export = null;
            local = null;
        }
    }

    public ClassLoader getExportClassLoader() {
        buildEffectiveLoaders();
        return effectiveExportClassLoader;
    }

    public ClassLoader getLocalClassLoader() {
        buildEffectiveLoaders();
        return effectiveLocalClassLoader;
    }

    public ClassLoaderScope getParent() {
        return parent;
    }

    public Factory<? extends ClassLoader> loader(final ClassPath classPath) {
        return new Factory<ClassLoader>() {
            public ClassLoader create() {
                return classLoaderCache.get(getExportClassLoader(), classPath, null);
            }
        };
    }

    public ClassLoaderScope local(Factory<? extends ClassLoader> classLoader) {
        assertNotLocked();
        if (localClassLoader != null) {
            localClassLoader.addParent(classLoader.create());
        } else {
            local.add(classLoader);
        }

        return this;
    }

    public ClassLoaderScope export(Factory<? extends ClassLoader> classLoader) {
        assertNotLocked();
        if (exportingClassLoader != null) {
            exportingClassLoader.addParent(classLoader.create());
        } else {
            export.add(classLoader);
        }

        return this;
    }

    private void assertNotLocked() {
        if (locked) {
            throw new IllegalStateException("class loader scope is locked");
        }
    }

    public ClassLoaderScope createSibling() {
        return new DefaultClassLoaderScope(parent, classLoaderCache);
    }

    public ClassLoaderScope createChild() {
        return new DefaultClassLoaderScope(this, classLoaderCache);
    }

    public ClassLoaderScope lock() {
        locked = true;
        return this;
    }

    public boolean isLocked() {
        return locked;
    }

}
