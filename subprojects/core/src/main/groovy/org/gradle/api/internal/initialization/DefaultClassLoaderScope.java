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

import org.gradle.api.internal.initialization.loadercache.ClassLoaderCache;
import org.gradle.api.internal.initialization.loadercache.ClassLoaderId;
import org.gradle.internal.classloader.CachingClassLoader;
import org.gradle.internal.classloader.MultiParentClassLoader;
import org.gradle.internal.classpath.ClassPath;
import org.gradle.internal.classpath.DefaultClassPath;

import java.util.ArrayList;
import java.util.List;

public class DefaultClassLoaderScope implements ClassLoaderScope {

    public static final String STRICT_MODE_PROPERTY = "org.gradle.classloaderscope.strict";

    final ClassLoaderScopeIdentifier id;
    private final ClassLoaderScope parent;
    private final ClassLoaderCache classLoaderCache;

    private boolean locked;

    private ClassPath export = new DefaultClassPath();
    private ClassPath local = new DefaultClassPath();
    private List<ClassLoader> ownLoaders = new ArrayList<ClassLoader>();

    // If these are not null, we are pessimistic (loaders asked for before locking)
    private MultiParentClassLoader exportingClassLoader;
    private MultiParentClassLoader localClassLoader;

    // What is actually exposed
    private ClassLoader effectiveLocalClassLoader;
    private ClassLoader effectiveExportClassLoader;

    public DefaultClassLoaderScope(ClassLoaderScopeIdentifier id, ClassLoaderScope parent, ClassLoaderCache classLoaderCache) {
        this.id = id;
        this.parent = parent;
        this.classLoaderCache = classLoaderCache;
    }

    private ClassLoader buildLockedLoader(ClassLoaderId id, ClassPath classPath) {
        if (classPath.isEmpty()) {
            return parent.getExportClassLoader();
        }
        return loader(id, classPath);
    }

    private ClassLoader buildLockedLoader(ClassLoaderId id, ClassLoader additional, ClassPath classPath) {
        if (classPath.isEmpty()) {
            return additional;
        }
        return new CachingClassLoader(new MultiParentClassLoader(additional, loader(id, classPath)));
    }

    private void buildEffectiveLoaders() {
        if (effectiveLocalClassLoader == null) {
            if (locked) {
                if (local.isEmpty() && export.isEmpty()) {
                    classLoaderCache.remove(id.localId());
                    classLoaderCache.remove(id.exportId());
                    effectiveLocalClassLoader = parent.getExportClassLoader();
                    effectiveExportClassLoader = parent.getExportClassLoader();
                } else if (export.isEmpty()) {
                    classLoaderCache.remove(id.exportId());
                    effectiveLocalClassLoader = buildLockedLoader(id.localId(), local);
                    effectiveExportClassLoader = parent.getExportClassLoader();
                } else if (local.isEmpty()) {
                    classLoaderCache.remove(id.localId());
                    effectiveLocalClassLoader = buildLockedLoader(id.exportId(), export);
                    effectiveExportClassLoader = effectiveLocalClassLoader;
                } else {
                    effectiveExportClassLoader = buildLockedLoader(id.exportId(), export);
                    effectiveLocalClassLoader = buildLockedLoader(id.localId(), effectiveExportClassLoader, local);
                }
            } else { // creating before locking, have to create the most flexible setup
                if (Boolean.getBoolean(STRICT_MODE_PROPERTY)) {
                    throw new IllegalStateException("Attempt to define scope class loader before scope is locked");
                }

                exportingClassLoader = new MultiParentClassLoader(parent.getExportClassLoader(), loader(id.exportId(), export));
                effectiveExportClassLoader = new CachingClassLoader(exportingClassLoader);

                localClassLoader = new MultiParentClassLoader(effectiveExportClassLoader, loader(id.localId(), local));
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

    @Override
    public boolean defines(Class<?> clazz) {
        for (ClassLoader ownLoader : ownLoaders) {
            if (ownLoader.equals(clazz.getClassLoader())) {
                return true;
            }
        }
        return false;
    }

    private ClassLoader loader(ClassLoaderId id, ClassPath classPath) {
        ClassLoader classLoader = classLoaderCache.get(id, classPath, parent.getExportClassLoader(), null);
        ownLoaders.add(classLoader);
        return classLoader;
    }

    public ClassLoaderScope local(ClassPath classPath) {
        if (classPath.isEmpty()) {
            return this;
        }

        assertNotLocked();
        if (localClassLoader != null) {
            ClassLoader loader = loader(id.localId(), classPath);
            localClassLoader.addParent(loader);
        } else {
            local = local.plus(classPath);
        }

        return this;
    }

    public ClassLoaderScope export(ClassPath classPath) {
        if (classPath.isEmpty()) {
            return this;
        }

        assertNotLocked();
        if (exportingClassLoader != null) {
            exportingClassLoader.addParent(loader(id.exportId(), classPath));
        } else {
            export = export.plus(classPath);
        }

        return this;
    }

    private void assertNotLocked() {
        if (locked) {
            throw new IllegalStateException("class loader scope is locked");
        }
    }

    public ClassLoaderScope createChild(String name) {
        if (name == null) {
            throw new IllegalArgumentException("'name' cannot be null");
        }
        return new DefaultClassLoaderScope(id.child(name), this, classLoaderCache);
    }

    public ClassLoaderScope lock() {
        locked = true;
        return this;
    }

    public boolean isLocked() {
        return locked;
    }
}
