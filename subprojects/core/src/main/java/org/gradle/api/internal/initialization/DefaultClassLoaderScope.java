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
import org.gradle.internal.classloader.ClassLoaderFactory;
import org.gradle.internal.classloader.ClassLoaderUtils;
import org.gradle.internal.classloader.DefaultClassLoaderFactory;
import org.gradle.internal.classloader.MultiParentClassLoader;
import org.gradle.internal.classpath.ClassPath;

import java.io.Closeable;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

public class DefaultClassLoaderScope implements ClassLoaderScope, Closeable {

    public static final String STRICT_MODE_PROPERTY = "org.gradle.classloaderscope.strict";

    final ClassLoaderScopeIdentifier id;
    private final ClassLoaderScope parent;
    private final ClassLoaderCache classLoaderCache;
    private final ClassLoaderFactory classLoaderFactory;

    private boolean locked;

    private ClassPath export; // This ClassPath is cumulative! Contains all parent classpaths excluding the root.
    private List<ClassLoader> exportLoaders; // if not null, is not empty
    private ClassPath local = ClassPath.EMPTY;

    // These are the ClassLoaders we create. Reachability is determined by the classloader hierarchy and the cache.
    private List<WeakReference<ClassLoader>> ownLoaders;

    // If these are not null, we are pessimistic (loaders asked for before locking)
    private MultiParentClassLoader exportingClassLoader;
    private MultiParentClassLoader localClassLoader;

    // What is actually exposed
    private ClassLoader effectiveLocalClassLoader;
    private ClassLoader effectiveExportClassLoader;

    public DefaultClassLoaderScope(ClassLoaderScopeIdentifier id, ClassLoaderScope parent, ClassLoaderCache classLoaderCache) {
        this.id = id;
        this.parent = parent;
        this.export = (parent == null) ? ClassPath.EMPTY : parent.getExportClassPath();
        this.classLoaderCache = classLoaderCache;
        this.classLoaderFactory = new DefaultClassLoaderFactory();
    }

    private ClassLoader buildLockedLoader(ClassLoaderId id, ClassPath classPath) {
        if (classPath.isEmpty()) {
            return createExportClassLoader(export);
        }
        return loader(id, classPath);
    }

    private ClassLoader buildLockedLoader(ClassLoaderId id, ClassLoader additional, ClassPath classPath) {
        if (classPath.isEmpty()) {
            return additional;
        }
        return new CachingClassLoader(new MultiParentClassLoader(additional, loader(id, classPath)));
    }

    private ClassLoader buildLockedLoader(ClassLoaderId id, ClassPath classPath, List<ClassLoader> loaders) {
        if (loaders != null) {
            return new CachingClassLoader(buildMultiLoader(id, classPath, loaders));
        } else if (!classPath.isEmpty()) {
            return buildLockedLoader(id, classPath);
        } else {
            return createExportClassLoader(export);
        }
    }

    private MultiParentClassLoader buildMultiLoader(ClassLoaderId id, ClassPath classPath, List<ClassLoader> loaders) {
        int numParents = 1;
        if (loaders != null) {
            numParents += loaders.size();
        }
        if (!classPath.isEmpty()) {
            numParents += 1;
        }
        List<ClassLoader> parents = new ArrayList<ClassLoader>(numParents);
        parents.add(createExportClassLoader(classPath));
        if (loaders != null) {
            parents.addAll(loaders);
        }
        if (!classPath.isEmpty()) {
            parents.add(loader(id, classPath));
        }
        return new MultiParentClassLoader(parents);
    }

    private void buildEffectiveLoaders() {
        if (effectiveLocalClassLoader == null) {
            boolean hasExports = !export.isEmpty() || exportLoaders != null;
            boolean hasLocals = !local.isEmpty();
            if (locked) {
                if (hasExports && hasLocals) {
                    effectiveExportClassLoader = buildLockedLoader(id.exportId(), export, exportLoaders);
                    effectiveLocalClassLoader = buildLockedLoader(id.localId(), effectiveExportClassLoader, local);
                } else if (hasLocals) {
                    classLoaderCache.remove(id.exportId());
                    effectiveLocalClassLoader = buildLockedLoader(id.localId(), local);
                    effectiveExportClassLoader = createExportClassLoader(export);
                } else if (hasExports) {
                    classLoaderCache.remove(id.localId());
                    effectiveLocalClassLoader = buildLockedLoader(id.exportId(), export, exportLoaders);
                    effectiveExportClassLoader = effectiveLocalClassLoader;
                } else {
                    classLoaderCache.remove(id.localId());
                    classLoaderCache.remove(id.exportId());
                    ClassLoader newExportClassLoader = createExportClassLoader(export);
                    effectiveLocalClassLoader = newExportClassLoader;
                    effectiveExportClassLoader = newExportClassLoader;
                }
            } else { // creating before locking, have to create the most flexible setup
                if (Boolean.getBoolean(STRICT_MODE_PROPERTY)) {
                    throw new IllegalStateException("Attempt to define scope class loader before scope is locked");
                }

                exportingClassLoader = buildMultiLoader(id.exportId(), export, exportLoaders);
                effectiveExportClassLoader = new CachingClassLoader(exportingClassLoader);

                localClassLoader = new MultiParentClassLoader(effectiveExportClassLoader, loader(id.localId(), local));
                effectiveLocalClassLoader = new CachingClassLoader(localClassLoader);
            }

            exportLoaders = null;
        }
    }

    @Override
    public ClassLoader getExportClassLoader() {
        buildEffectiveLoaders();
        return effectiveExportClassLoader;
    }

    @Override
    public ClassLoader getLocalClassLoader() {
        buildEffectiveLoaders();
        return effectiveLocalClassLoader;
    }

    @Override
    public ClassLoaderScope getParent() {
        return parent;
    }

    @Override
    public boolean defines(Class<?> clazz) {
        if (ownLoaders != null) {
            for (WeakReference<ClassLoader> ownLoader : ownLoaders) {
                ClassLoader loader = ownLoader.get();
                if (loader != null && loader.equals(clazz.getClassLoader())) {
                    return true;
                }
            }
        }
        return false;
    }

    private ClassLoader loader(ClassLoaderId id, ClassPath classPath) {
        ClassLoader classLoader = classLoaderCache.get(id, classPath, parent.getExportClassLoader(), null);
        addOwnLoader(classLoader);
        return classLoader;
    }

    private void addOwnLoader(ClassLoader loader) {
        if (ownLoaders == null) {
            ownLoaders = new ArrayList<WeakReference<ClassLoader>>();
        }
        ownLoaders.add(new WeakReference<ClassLoader>(loader));
    }

    @Override
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

    @Override
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

    @Override
    public ClassLoaderScope export(ClassLoader classLoader) {
        assertNotLocked();
        if (exportingClassLoader != null) {
            exportingClassLoader.addParent(classLoader);
        } else {
            if (exportLoaders == null) {
                exportLoaders = new ArrayList<ClassLoader>(1);
            }
            exportLoaders.add(classLoader);
        }

        return this;
    }

    @Override
    public ClassPath getExportClassPath() {
        return ClassPath.EMPTY.plus(export);
    }

    private void assertNotLocked() {
        if (locked) {
            throw new IllegalStateException("class loader scope is locked");
        }
    }

    @Override
    public ClassLoaderScope createChild(String name) {
        if (name == null) {
            throw new IllegalArgumentException("'name' cannot be null");
        }
        return new DefaultClassLoaderScope(id.child(name), this, classLoaderCache);
    }

    private ClassLoader createExportClassLoader(ClassPath classPath) {
        ClassLoader newExportingLoader = classLoaderFactory.createClassLoader(getParent().getExportClassLoader(), classPath);
        addOwnLoader(newExportingLoader);
        return newExportingLoader;
    }

    @Override
    public ClassLoaderScope lock() {
        locked = true;
        return this;
    }

    @Override
    public boolean isLocked() {
        return locked;
    }

    @Override
    public void close() {
        // TODO: The local ClassLoader isn't causing problems, so I'm leaving it open for now.
        ClassLoaderUtils.tryClose(effectiveExportClassLoader);
        classLoaderCache.clear();

        if (ownLoaders != null) {
            ownLoaders.clear();
        }
    }
}
