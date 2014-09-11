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

package org.gradle.api.internal.tasks.compile.incremental.jar;

import org.gradle.api.internal.cache.SingleOperationPersistentStore;
import org.gradle.cache.CacheRepository;

//Keeps the jar classpath snapshot of given compile task
public class LocalJarClasspathSnapshotStore {

    private final SingleOperationPersistentStore<JarClasspathSnapshotData> store;

    public LocalJarClasspathSnapshotStore(CacheRepository cacheRepository, Object scope) {
        //Single operation store that we throw away after the operation makes the implementation simpler.
        store = new SingleOperationPersistentStore<JarClasspathSnapshotData>(cacheRepository, scope, "local jar classpath snapshot", new JarClasspathSnapshotDataSerializer());
    }

    public void put(JarClasspathSnapshotData data) {
        store.putAndClose(data);
    }

    public JarClasspathSnapshotData get() {
        return store.getAndClose();
    }
}