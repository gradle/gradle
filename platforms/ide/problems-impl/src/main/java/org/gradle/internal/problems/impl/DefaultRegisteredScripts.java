/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.internal.problems.impl;

import org.gradle.api.internal.initialization.loadercache.ClassLoaderId;
import org.gradle.initialization.ClassLoaderScopeId;
import org.gradle.initialization.ClassLoaderScopeOrigin;
import org.gradle.initialization.ClassLoaderScopeRegistryListener;
import org.gradle.initialization.ClassLoaderScopeRegistryListenerManager;
import org.gradle.internal.classpath.ClassPath;
import org.gradle.internal.hash.HashCode;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.io.Closeable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Records build script sources as their class loader scopes are created, so a source file name can be
 * resolved back to its script for the lifetime of the build session.
 */
@NullMarked
class DefaultRegisteredScripts implements RegisteredScripts, ClassLoaderScopeRegistryListener, Closeable {

    private final Map<String, ClassLoaderScopeOrigin.Script> scriptsByFileName = new ConcurrentHashMap<>();
    private final ClassLoaderScopeRegistryListenerManager listenerManager;

    public DefaultRegisteredScripts(ClassLoaderScopeRegistryListenerManager listenerManager) {
        this.listenerManager = listenerManager;
        listenerManager.add(this);
    }

    @Override
    public ClassLoaderScopeOrigin.@Nullable Script scriptFor(String fileName) {
        return scriptsByFileName.get(fileName);
    }

    @Override
    public void childScopeCreated(ClassLoaderScopeId parentId, ClassLoaderScopeId childId, @Nullable ClassLoaderScopeOrigin origin) {
        if (origin instanceof ClassLoaderScopeOrigin.Script script) {
            scriptsByFileName.put(script.getFileName(), script);
        }
    }

    @Override
    public void classloaderCreated(ClassLoaderScopeId scopeId, ClassLoaderId classLoaderId, ClassLoader classLoader, ClassPath classPath, @Nullable HashCode implementationHash) {
    }

    @Override
    public void close() {
        listenerManager.remove(this);
        scriptsByFileName.clear();
    }
}
