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

import org.gradle.internal.classloader.CachingClassLoader;
import org.gradle.internal.classloader.MultiParentClassLoader;
import org.gradle.internal.classpath.ClassPath;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class DefaultClassLoaderScope implements ClassLoaderScope {

    public static final String STRICT_MODE_PROPERTY = "org.gradle.classloaderscope.strict";

    private final ClassLoaderScope parent;
    private final ClassLoaderCache classLoaderCache;

    private boolean locked;

    private List<ClassPath> export = new LinkedList<ClassPath>();
    private List<ClassPath> local = new LinkedList<ClassPath>();

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

    private ClassLoader buildLockedLoader(List<ClassPath> classPaths) {
        if (classPaths.isEmpty()) {
            return parent.getExportClassLoader();
        }
        if (classPaths.size() == 1) {
            return loader(classPaths.get(0));
        }

        List<ClassLoader> loaders = new ArrayList<ClassLoader>(classPaths.size());
        for (ClassPath classPath : classPaths) {
            loaders.add(loader(classPath));
        }
        return new CachingClassLoader(new MultiParentClassLoader(loaders));
    }

    private ClassLoader buildLockedLoader(ClassLoader additional, List<ClassPath> classPaths) {
        if (classPaths.isEmpty()) {
            return additional;
        }

        List<ClassLoader> loaders = new ArrayList<ClassLoader>(classPaths.size() + 1);
        loaders.add(additional);
        for (ClassPath classPath : classPaths) {
            loaders.add(loader(classPath));
        }
        return new CachingClassLoader(new MultiParentClassLoader(loaders));
    }

    private MultiParentClassLoader buildOpenLoader(ClassLoader additional, List<ClassPath> classPaths) {
        List<ClassLoader> loaders = new ArrayList<ClassLoader>(classPaths.size() + 1);
        loaders.add(additional);
        for (ClassPath classPath : classPaths) {
            loaders.add(loader(classPath));
        }
        return new MultiParentClassLoader(loaders);
    }

    private void buildEffectiveLoaders() {
        if (effectiveLocalClassLoader == null) {
            if (locked) {
                if (local.isEmpty() && export.isEmpty()) {
                    effectiveLocalClassLoader = parent.getExportClassLoader();
                    effectiveExportClassLoader = parent.getExportClassLoader();
                } else if (export.isEmpty()) {
                    effectiveLocalClassLoader = buildLockedLoader(local);
                    effectiveExportClassLoader = parent.getExportClassLoader();
                } else if (local.isEmpty()) {
                    effectiveLocalClassLoader = buildLockedLoader(export);
                    effectiveExportClassLoader = effectiveLocalClassLoader;
                } else {
                    effectiveExportClassLoader = buildLockedLoader(export);
                    effectiveLocalClassLoader = buildLockedLoader(effectiveExportClassLoader, local);
                }
            } else { // creating before locking, have to create the most flexible setup
                if (Boolean.getBoolean(STRICT_MODE_PROPERTY)) {
                    throw new IllegalStateException("Attempt to define scope class loader before scope is locked");
                }

                exportingClassLoader = buildOpenLoader(parent.getExportClassLoader(), export);
                effectiveExportClassLoader = new CachingClassLoader(exportingClassLoader);

                localClassLoader = buildOpenLoader(effectiveExportClassLoader, local);
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

    private ClassLoader loader(ClassPath classPath) {
        return classLoaderCache.get(parent.getExportClassLoader(), classPath, null);
    }

    public ClassLoaderScope local(ClassPath classPath) {
        if (classPath.isEmpty()) {
            return this;
        }

        assertNotLocked();
        if (localClassLoader != null) {
            localClassLoader.addParent(loader(classPath));
        } else {
            local.add(classPath);
        }

        return this;
    }

    public ClassLoaderScope export(ClassPath classPath) {
        if (classPath.isEmpty()) {
            return this;
        }

        assertNotLocked();
        if (exportingClassLoader != null) {
            exportingClassLoader.addParent(loader(classPath));
        } else {
            export.add(classPath);
        }

        return this;
    }

    private void assertNotLocked() {
        if (locked) {
            throw new IllegalStateException("class loader scope is locked");
        }
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
