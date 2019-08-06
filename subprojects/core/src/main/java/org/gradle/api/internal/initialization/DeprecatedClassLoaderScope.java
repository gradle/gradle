/*
 * Copyright 2019 the original author or authors.
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
import org.gradle.initialization.ClassLoaderScopeRegistryListener;
import org.gradle.internal.classpath.ClassPath;

public class DeprecatedClassLoaderScope extends DefaultClassLoaderScope {

    private ClassPath deprecatedClasspath;
    private ClassLoader deprecatedExportClassloader;
    private ClassLoader deprecatedLocalClassloader;

    public DeprecatedClassLoaderScope(ClassLoaderScopeIdentifier id, ClassLoaderScope parent, ClassLoaderCache classLoaderCache, ClassPath deprecatedClasspath, ClassLoaderScopeRegistryListener listener) {
        super(id, parent, classLoaderCache, listener);
        this.deprecatedClasspath = deprecatedClasspath;
    }

    @Override
    public ClassLoaderScope export(ClassPath classPath) {
        export = export.plus(classPath);
        exportClasspathAdded(classPath);
        return this;
    }

    @Override
    public ClassLoaderScope deprecated() {
        return this;
    }

    @Override
    public ClassLoader getExportClassLoader() {
        if (deprecatedExportClassloader == null) {
            ClassLoaderId id = this.id.exportId();
            deprecatedExportClassloader = new DefaultDeprecatedClassLoader(buildLockedLoader(id, deprecatedClasspath), super.getExportClassLoader());
            classLoaderCache.put(id, deprecatedExportClassloader);
        }
        return deprecatedExportClassloader;
    }

    @Override
    public ClassLoader getLocalClassLoader() {
        if (deprecatedLocalClassloader == null) {
            ClassLoaderId id = this.id.localId();
            deprecatedLocalClassloader = new DefaultDeprecatedClassLoader(buildLockedLoader(id, deprecatedClasspath), super.getLocalClassLoader());
            classLoaderCache.put(id, deprecatedLocalClassloader);
        }
        return deprecatedLocalClassloader;
    }

    @Override
    public ClassLoaderScope createChild(String name) {
        ClassLoaderScopeIdentifier childId = id.child(name);
        childScopeCreated(childId);
        return new DeprecatedClassLoaderScope(childId, this, classLoaderCache, deprecatedClasspath, listener);
    }
}
