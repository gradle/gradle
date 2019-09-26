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

import org.gradle.api.Transformer;
import org.gradle.api.internal.initialization.loadercache.ClassLoaderCache;
import org.gradle.api.internal.initialization.loadercache.ClassLoaderId;
import org.gradle.initialization.ClassLoaderScopeRegistryListener;
import org.gradle.internal.classloader.CachingClassLoader;
import org.gradle.internal.classloader.MultiParentClassLoader;
import org.gradle.internal.classpath.ClassPath;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

public class DefaultClassLoaderScope extends AbstractClassLoaderScope {

    public static final String STRICT_MODE_PROPERTY = "org.gradle.classloaderscope.strict";

    private final ClassLoaderScope parent;

    private boolean locked;

    protected ClassPath export = ClassPath.EMPTY;
    private List<ClassLoader> exportLoaders; // if not null, is not empty
    private ClassPath local = ClassPath.EMPTY;
    private List<ClassLoader> ownLoaders;

    private Transformer<ClassLoader, ClassLoader> localClassLoaderFactory;

    // If these are not null, we are pessimistic (loaders asked for before locking)
    private MultiParentClassLoader exportingClassLoader;
    private MultiParentClassLoader localClassLoader;

    // What is actually exposed
    private ClassLoader effectiveLocalClassLoader;
    private ClassLoader effectiveExportClassLoader;

    public DefaultClassLoaderScope(ClassLoaderScopeIdentifier id, ClassLoaderScope parent, ClassLoaderCache classLoaderCache, ClassLoaderScopeRegistryListener listener) {
        super(id, classLoaderCache, listener);
        this.parent = parent;
    }

    private ClassLoader loader(ClassLoaderId id, ClassLoader parent, ClassPath classPath, @Nullable List<ClassLoader> additionalLoaders) {
        if (additionalLoaders == null) {
            return loader(id, parent, classPath);
        }
        return new CachingClassLoader(multiLoader(id, parent, classPath, additionalLoaders));
    }

    private MultiParentClassLoader multiLoader(ClassLoaderId id, ClassLoader parent, ClassPath classPath, @Nullable List<ClassLoader> additionalLoaders) {
        int numParents = 1;
        if (additionalLoaders != null) {
            numParents += additionalLoaders.size();
        }
        if (!classPath.isEmpty()) {
            numParents += 1;
        }
        List<ClassLoader> parents = new ArrayList<>(numParents);
        parents.add(parent);
        if (additionalLoaders != null) {
            parents.addAll(additionalLoaders);
        }
        if (!classPath.isEmpty()) {
            parents.add(loader(id, parent, classPath));
        }
        return new MultiParentClassLoader(parents);
    }

    private ClassLoader localLoader(ClassLoaderId classLoaderId, ClassLoader parent, ClassPath classPath) {
        if (localClassLoaderFactory == null) {
            return this.loader(classLoaderId, parent, classPath);
        } else {
            ClassLoader loader = classLoaderCache.put(classLoaderId, localClassLoaderFactory.transform(parent));
            listener.classloaderCreated(id, classLoaderId, loader);
            return loader;
        }
    }

    private void buildEffectiveLoaders() {
        if (effectiveLocalClassLoader == null) {
            boolean hasExports = !export.isEmpty() || exportLoaders != null;
            boolean hasLocals = !local.isEmpty();
            if (locked) {
                if (hasExports && hasLocals) {
                    effectiveExportClassLoader = loader(id.exportId(), parent.getExportClassLoader(), export, exportLoaders);
                    effectiveLocalClassLoader = localLoader(id.localId(), effectiveExportClassLoader, local);
                } else if (hasLocals) {
                    classLoaderCache.remove(id.exportId());
                    effectiveExportClassLoader = parent.getExportClassLoader();
                    effectiveLocalClassLoader = localLoader(id.localId(), effectiveExportClassLoader, local);
                } else if (hasExports) {
                    classLoaderCache.remove(id.localId());
                    effectiveExportClassLoader = loader(id.exportId(), parent.getExportClassLoader(), export, exportLoaders);
                    effectiveLocalClassLoader = effectiveExportClassLoader;
                } else {
                    classLoaderCache.remove(id.localId());
                    classLoaderCache.remove(id.exportId());
                    effectiveLocalClassLoader = parent.getExportClassLoader();
                    effectiveExportClassLoader = parent.getExportClassLoader();
                }
            } else { // creating before locking, have to create the most flexible setup
                if (Boolean.getBoolean(STRICT_MODE_PROPERTY)) {
                    throw new IllegalStateException("Attempt to define scope class loader before scope is locked, scope identifier is " + id);
                }
                if (localClassLoaderFactory != null) {
                    throw new UnsupportedOperationException("Not implemented");
                }

                exportingClassLoader = multiLoader(id.exportId(), parent.getExportClassLoader(), export, exportLoaders);
                effectiveExportClassLoader = new CachingClassLoader(exportingClassLoader);

                localClassLoader = new MultiParentClassLoader(loader(id.localId(), effectiveExportClassLoader, local));
                effectiveLocalClassLoader = new CachingClassLoader(localClassLoader);
            }

            export = null;
            exportLoaders = null;
            local = null;
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
            for (ClassLoader ownLoader : ownLoaders) {
                if (ownLoader.equals(clazz.getClassLoader())) {
                    return true;
                }
            }
        }
        return false;
    }

    protected ClassLoader loader(ClassLoaderId id, ClassLoader parent, ClassPath classPath) {
        if (classPath.isEmpty()) {
            return parent;
        }
        ClassLoader classLoader = classLoaderCache.get(id, classPath, parent, null);
        listener.classloaderCreated(this.id, id, classLoader);
        if (ownLoaders == null) {
            ownLoaders = new ArrayList<>();
        }
        ownLoaders.add(classLoader);
        return classLoader;
    }

    @Override
    public ClassLoaderScope local(ClassPath classPath) {
        if (classPath.isEmpty()) {
            return this;
        }

        assertNotLocked();
        if (localClassLoader != null) {
            ClassLoader loader = loader(id.localId(), effectiveExportClassLoader, classPath);
            localClassLoader.addParent(loader);
        } else {
            local = local.plus(classPath);
        }

        localClasspathAdded(classPath);
        return this;
    }

    @Override
    public ClassLoaderScope export(ClassPath classPath) {
        if (classPath.isEmpty()) {
            return this;
        }

        assertNotLocked();
        if (exportingClassLoader != null) {
            exportingClassLoader.addParent(loader(id.exportId(), parent.getExportClassLoader(), classPath));
        } else {
            export = export.plus(classPath);
        }

        exportClasspathAdded(classPath);
        return this;
    }

    @Override
    public ClassLoaderScope export(ClassLoader classLoader) {
        assertNotLocked();
        if (exportingClassLoader != null) {
            exportingClassLoader.addParent(classLoader);
        } else {
            if (exportLoaders == null) {
                exportLoaders = new ArrayList<>(1);
            }
            exportLoaders.add(classLoader);
        }

        return this;
    }

    private void assertNotLocked() {
        if (locked) {
            throw new IllegalStateException("class loader scope is locked, scope identifier is " + id);
        }
    }

    @Override
    public ClassLoaderScope lock() {
        locked = true;
        return this;
    }

    @Override
    public ClassLoaderScope lock(Transformer<ClassLoader, ClassLoader> localClassLoaderFactory) {
        assertNotLocked();
        this.localClassLoaderFactory = localClassLoaderFactory;
        return lock();
    }

    @Override
    public boolean isLocked() {
        return locked;
    }

    protected void exportClasspathAdded(ClassPath classPath) {
        listener.exportClasspathAdded(id, classPath);
    }

    protected void localClasspathAdded(ClassPath classPath) {
        listener.localClasspathAdded(id, classPath);
    }
}
