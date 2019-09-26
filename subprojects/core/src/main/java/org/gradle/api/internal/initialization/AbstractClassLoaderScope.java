/*
 * Copyright 2016 the original author or authors.
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
import org.gradle.initialization.ClassLoaderScopeId;
import org.gradle.initialization.ClassLoaderScopeRegistryListener;

/**
 * Provides common {@link #getPath} and {@link #createChild} behaviour for {@link ClassLoaderScope} implementations.
 */
public abstract class AbstractClassLoaderScope implements ClassLoaderScope {

    protected final ClassLoaderScopeIdentifier id;
    protected final ClassLoaderCache classLoaderCache;
    protected final ClassLoaderScopeRegistryListener listener;

    protected AbstractClassLoaderScope(ClassLoaderScopeIdentifier id, ClassLoaderCache classLoaderCache, ClassLoaderScopeRegistryListener listener) {
        this.id = id;
        this.classLoaderCache = classLoaderCache;
        this.listener = listener;
    }

    /**
     * Unique identifier of this scope in the hierarchy.
     */
    public ClassLoaderScopeId getId() {
        return id;
    }

    /**
     * A string representing the path of this {@link ClassLoaderScope} in the {@link ClassLoaderScope} graph.
     */
    public String getPath() {
        return id.getPath();
    }

    @Override
    public ClassLoaderScope createChild(String name) {
        if (name == null) {
            throw new IllegalArgumentException("'name' cannot be null");
        }
        ClassLoaderScopeIdentifier childId = id.child(name);
        childScopeCreated(childId);
        return new DefaultClassLoaderScope(childId, this, classLoaderCache, listener);
    }

    protected void childScopeCreated(ClassLoaderScopeId childId) {
        listener.childScopeCreated(id, childId);
    }
}
