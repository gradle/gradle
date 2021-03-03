/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.initialization;

import org.gradle.api.internal.initialization.ClassLoaderScope;
import org.gradle.api.internal.initialization.loadercache.ClassLoaderId;
import org.gradle.internal.classpath.ClassPath;
import org.gradle.internal.hash.HashCode;

import javax.annotation.Nullable;

/**
 * A globablly scoped mutable reference to the ClassLoaderScopeRegistryListener for a build tree.
 *
 * This is necessary due to the cross build caching of class loader scopes by script caching infrastructure.
 * The scope registry itself is build tree scoped, as is the ClassLoaderScopeRegistryListener.
 * Injecting the listener retutned by {@link #get()} into the scope registry allows listeners from subsequent
 * builds to be notified about scopes/loaders that are being reused from previous builds.
 *
 * See {@link ClassLoaderScope#onReuse()} for scope reuse sites.
 */
public class ClassLoaderScopeRegistryListenerGlobalRef {

    private static final ClassLoaderScopeRegistryListener NULL = new ClassLoaderScopeRegistryListener() {
        @Override
        public void rootScopeCreated(ClassLoaderScopeId rootScopeId) {

        }

        @Override
        public void childScopeCreated(ClassLoaderScopeId parentId, ClassLoaderScopeId childId) {

        }

        @Override
        public void classloaderCreated(ClassLoaderScopeId scopeId, ClassLoaderId classLoaderId, ClassLoader classLoader, ClassPath classPath, @Nullable HashCode implementationHash) {

        }
    };

    private ClassLoaderScopeRegistryListener delegate = NULL;
    private final ClassLoaderScopeRegistryListener listener = new ClassLoaderScopeRegistryListener() {
        public void rootScopeCreated(ClassLoaderScopeId rootScopeId) {
            delegate.rootScopeCreated(rootScopeId);
        }

        public void childScopeCreated(ClassLoaderScopeId parentId, ClassLoaderScopeId childId) {
            delegate.childScopeCreated(parentId, childId);
        }

        public void classloaderCreated(ClassLoaderScopeId scopeId, ClassLoaderId classLoaderId, ClassLoader classLoader, ClassPath classPath, @Nullable HashCode implementationHash) {
            delegate.classloaderCreated(scopeId, classLoaderId, classLoader, classPath, implementationHash);
        }
    };

    public ClassLoaderScopeRegistryListener get() {
        return listener;
    }

    // The returned handle is only used to release the reference to the delegate
    public BuildTreeScopeHandle set(ClassLoaderScopeRegistryListener delegate) {
        this.delegate = delegate;
        return new BuildTreeScopeHandle();
    }

    public class BuildTreeScopeHandle implements AutoCloseable {
        @Override
        public void close() {
            delegate = NULL;
        }
    }

}
