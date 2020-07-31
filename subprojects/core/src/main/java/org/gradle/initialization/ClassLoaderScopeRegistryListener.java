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

package org.gradle.initialization;

import org.gradle.api.internal.initialization.loadercache.ClassLoaderId;
import org.gradle.internal.classpath.ClassPath;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.service.scopes.EventScope;
import org.gradle.internal.service.scopes.Scopes;

import javax.annotation.Nullable;


/**
 * Listens to changes to the ClassLoaderScope tree.
 *
 * Must be registered with the {@link org.gradle.internal.event.ListenerManager} during
 * a {@link org.gradle.internal.service.scopes.BuildScopeListenerManagerAction}.
 *
 * @see ClassLoaderScopeRegistry
 * @see org.gradle.api.internal.initialization.ClassLoaderScope
 */
@EventScope(Scopes.Build.class)
public interface ClassLoaderScopeRegistryListener {

    void rootScopeCreated(ClassLoaderScopeId rootScopeId);

    void childScopeCreated(ClassLoaderScopeId parentId, ClassLoaderScopeId childId);

    void classloaderCreated(ClassLoaderScopeId scopeId, ClassLoaderId classLoaderId, ClassLoader classLoader, ClassPath classPath, @Nullable HashCode implementationHash);

    ClassLoaderScopeRegistryListener NULL = new ClassLoaderScopeRegistryListener() {
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
}
