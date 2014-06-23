/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.api.internal.tasks.compile.incremental.cache;

import org.gradle.api.internal.cache.SingleOperationPersistentStore;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.cache.CacheRepository;

import java.io.File;
import java.util.Map;

//Keeps the jar hashes of given compile task
public class LocalJarHashesStore {

    private final SingleOperationPersistentStore<Map<File, byte[]>> store;

    public LocalJarHashesStore(CacheRepository cacheRepository, JavaCompile javaCompile) {
        //Single operation store that we throw away after the operation makes the implementation simpler.
        store = new SingleOperationPersistentStore<Map<File, byte[]>>(cacheRepository, javaCompile, "local jar hashes", (Class) Map.class);
    }

    public void put(Map<File, byte[]> newHashes) {
        store.putAndClose(newHashes);
    }

    public Map<File, byte[]> get() {
        return store.getAndClose();
    }
}