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
import java.util.List;

public class DefaultClassLoaderScope implements ClassLoaderScope {

    public static final String STRICT_MODE_PROPERTY = "org.gradle.classloaderscope.strict";

    private final ClassLoaderScope parent;
    private final ClassLoaderScope base;
    private final ClassLoaderCache classLoaderCache;

    private boolean locked;

    private List<ClassLoader> local;

    private ClassLoader scopeClassLoader;

    private MultiParentClassLoader exportingClassLoader;
    private MultiParentClassLoader localClassLoader;
    private ClassLoader childrenParent;

    public DefaultClassLoaderScope(ClassLoaderScope parent, ClassLoaderScope base, ClassLoaderCache classLoaderCache) {
        this.parent = parent;
        this.base = base;
        this.classLoaderCache = classLoaderCache;
    }

    public ClassLoader getChildClassLoader() {
        getScopeClassLoader(); // trigger calculation
        return childrenParent;
    }

    public ClassLoaderScope getBase() {
        return base;
    }

    public ClassLoader getScopeClassLoader() {
        if (scopeClassLoader == null) {
            if (locked) {
                if (local == null && exportingClassLoader == null) { // best case, no additions
                    scopeClassLoader = parent.getChildClassLoader();
                    childrenParent = scopeClassLoader;
                } else if (exportingClassLoader == null) { // no impact on children
                    localClassLoader = new MultiParentClassLoader(parent.getChildClassLoader());
                    scopeClassLoader = new CachingClassLoader(localClassLoader);
                    childrenParent = parent.getChildClassLoader();
                } else if (local == null) {
                    scopeClassLoader = exportingClassLoader;
                    childrenParent = scopeClassLoader;
                } else {
                    createFlexibleLoaderStructure();
                }
            } else { // creating before locking, have to create the most flexible setup
                if (Boolean.getBoolean(STRICT_MODE_PROPERTY)) {
                    throw new IllegalStateException("Attempt to define scope class loader before scope is locked");
                }

                createFlexibleLoaderStructure();
            }

            if (local != null) {
                for (ClassLoader localClassLoader : local) {
                    addLocal(localClassLoader);
                }
            }
        }

        return scopeClassLoader;
    }

    private void addLocal(ClassLoader newClassLoader) {
        assert localClassLoader != null;
        localClassLoader.addParent(newClassLoader);
    }

    private void createFlexibleLoaderStructure() {
        if (exportingClassLoader == null) {
            exportingClassLoader = new MultiParentClassLoader(parent.getChildClassLoader());
        }

        localClassLoader = new MultiParentClassLoader(exportingClassLoader);
        scopeClassLoader = new CachingClassLoader(localClassLoader);
        childrenParent = exportingClassLoader;
    }

    public ClassLoader addLocal(ClassPath classpath) {
        if (locked) {
            throw new IllegalStateException("class loader scope is locked");
        }
        if (!classpath.isEmpty()) {
            if (local == null) {
                local = new ArrayList<ClassLoader>(1);
            }

            ClassLoader newClassLoader = classLoaderCache.get(base.getChildClassLoader(), classpath, null);
            local.add(newClassLoader);

            if (localClassLoader != null) { // classloader was eagerly created, have to add
                addLocal(newClassLoader);
            }

            return newClassLoader;
        } else {
            return base.getChildClassLoader();
        }
    }

    public ClassLoader export(ClassPath classpath) {
        if (locked) {
            throw new IllegalStateException("class loader scope is locked");
        }
        if (classpath.isEmpty()) {
            return parent.getChildClassLoader();
        } else {
            if (exportingClassLoader == null) {
                exportingClassLoader = new MultiParentClassLoader();
            }

            ClassLoader classLoader = classLoaderCache.get(parent.getChildClassLoader(), classpath, null);
            exportingClassLoader.addParent(classLoader);
            return classLoader;
        }
    }

    public ClassLoaderScope createSibling() {
        return new DefaultClassLoaderScope(parent, base, classLoaderCache);
    }

    public ClassLoaderScope createChild() {
        return new DefaultClassLoaderScope(this, base, classLoaderCache);
    }

    public ClassLoaderScope createRebasedChild() {
        return new DefaultClassLoaderScope(this, this, classLoaderCache);
    }

    public ClassLoaderScope lock() {
        locked = true;
        return this;
    }

    public boolean isLocked() {
        return locked;
    }

}
