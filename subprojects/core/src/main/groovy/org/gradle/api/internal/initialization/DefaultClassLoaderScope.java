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
import org.gradle.internal.classloader.MutableURLClassLoader;
import org.gradle.internal.classpath.ClassPath;

import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;

public class DefaultClassLoaderScope implements ClassLoaderScope {

    private final ClassLoaderScope parent;
    private final ClassLoaderScope base;

    private boolean locked;

    private List<ClassLoader> local;

    private ClassLoader classLoader;

    private MutableURLClassLoader exportingClassLoader;
    private MultiParentClassLoader localClassLoader;
    private ClassLoader childrenParent;

    public DefaultClassLoaderScope(ClassLoaderScope parent, ClassLoaderScope base) {
        this.parent = parent;
        this.base = base;
    }

    public ClassLoader getChildClassLoader() {
        getScopeClassLoader(); // trigger calculation
        return childrenParent;
    }

    public ClassLoaderScope getBase() {
        return base;
    }

    public ClassLoader getScopeClassLoader() {
        if (classLoader == null) {
            if (locked) {
                if (local == null && exportingClassLoader == null) { // best case, no additions
                    classLoader = parent.getChildClassLoader();
                    childrenParent = classLoader;
                } else if (exportingClassLoader == null) { // no impact on children
                    localClassLoader = new MultiParentClassLoader(parent.getChildClassLoader());
                    classLoader = new CachingClassLoader(localClassLoader);
                    childrenParent = parent.getChildClassLoader();
                } else if (local == null) {
                    classLoader = exportingClassLoader;
                    childrenParent = classLoader;
                } else {
                    createFlexibleLoaderStructure();
                }
            } else { // creating before locking, have to create the most flexible setup
                createFlexibleLoaderStructure();
            }

            if (local != null) {
                for (ClassLoader localClassLoader : local) {
                    addLocal(localClassLoader);
                }
            }
        }

        return classLoader;
    }

    private void addLocal(ClassLoader newClassLoader) {
        assert localClassLoader != null;
        localClassLoader.addParent(newClassLoader);
    }

    private void createFlexibleLoaderStructure() {
        if (exportingClassLoader == null) {
            exportingClassLoader = new MutableURLClassLoader(parent.getChildClassLoader());
        }

        localClassLoader = new MultiParentClassLoader(exportingClassLoader);
        classLoader = new CachingClassLoader(localClassLoader);
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

            URLClassLoader newClassLoader = new URLClassLoader(classpath.getAsURLArray(), base.getChildClassLoader());
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
        if (exportingClassLoader != null) {
            throw new IllegalStateException("class loader scope can only export one classpath");
        }
        if (classpath.isEmpty()) {
            return parent.getChildClassLoader();
        } else {
            if (exportingClassLoader == null) {
                exportingClassLoader = new MutableURLClassLoader(parent.getChildClassLoader());
            }
            exportingClassLoader.addURLs(classpath.getAsURLs());
            return exportingClassLoader;
        }
    }

    public ClassLoaderScope createSibling() {
        return new DefaultClassLoaderScope(parent, base);
    }

    public ClassLoaderScope createChild() {
        return new DefaultClassLoaderScope(this, base);
    }

    public ClassLoaderScope createRebasedChild() {
        return new DefaultClassLoaderScope(this, this);
    }

    public void lock() {
        locked = true;
    }

    public boolean isLocked() {
        return locked;
    }

}
