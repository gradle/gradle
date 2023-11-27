/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.language.cpp.internal;

import org.gradle.cache.FileLockManager;
import org.gradle.cache.PersistentCache;
import org.gradle.cache.internal.filelock.LockOptionsBuilder;
import org.gradle.cache.scopes.GlobalScopedCacheBuilderFactory;
import org.gradle.internal.Factory;
import org.gradle.internal.concurrent.Stoppable;
import org.gradle.nativeplatform.internal.modulemap.ModuleMap;

import java.io.File;

import static org.gradle.nativeplatform.internal.modulemap.GenerateModuleMapFile.generateFile;

/**
 * This is intended to be temporary, until more metadata can be published and the dependency resolution engine can deal with it. As such, it's not particularly performant or robust.
 */
public class NativeDependencyCache implements Stoppable {
    private final PersistentCache cache;

    public NativeDependencyCache(GlobalScopedCacheBuilderFactory cacheBuilderFactory) {
        cache = cacheBuilderFactory.createCacheBuilder("native-dep").withLockOptions(LockOptionsBuilder.mode(FileLockManager.LockMode.OnDemand)).open();
    }

    public File getModuleMapFile(final ModuleMap moduleMap) {
        final String hash = moduleMap.getHashCode().toCompactString();
        return cache.useCache(new Factory<File>() {
            @Override
            public File create() {
                File dir = new File(cache.getBaseDir(), "maps/" + hash + "/" + moduleMap.getModuleName());
                File moduleMapFile = new File(dir, "module.modulemap");
                if (!moduleMapFile.isFile()) {
                    generateFile(moduleMapFile, moduleMap.getModuleName(), moduleMap.getPublicHeaderPaths());
                }
                return moduleMapFile;
            }
        });
    }

    @Override
    public void stop() {
        cache.close();
    }
}
