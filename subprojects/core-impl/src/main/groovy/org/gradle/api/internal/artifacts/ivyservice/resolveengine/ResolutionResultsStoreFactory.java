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

package org.gradle.api.internal.artifacts.ivyservice.resolveengine;

import com.google.common.collect.MapMaker;
import org.gradle.api.artifacts.result.ResolvedModuleVersionResult;
import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.oldresult.CachedStoreFactory;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.oldresult.TransientConfigurationResults;
import org.gradle.api.internal.cache.BinaryStore;
import org.gradle.api.internal.cache.Store;
import org.gradle.api.internal.file.TemporaryFileProvider;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.util.Clock;

import java.io.*;
import java.util.concurrent.ConcurrentMap;

import static org.gradle.internal.UncheckedException.throwAsUncheckedException;

//Draft, needs rework, along with BinaryStore interface, etc.
public class ResolutionResultsStoreFactory implements Closeable {
    private final static Logger LOG = Logging.getLogger(ResolutionResultsStoreFactory.class);

    private final TemporaryFileProvider temp;
    private final CachedStoreFactory<TransientConfigurationResults> oldModelCache =
            new CachedStoreFactory<TransientConfigurationResults>("Resolution result");
    private final CachedStoreFactory<ResolvedModuleVersionResult> newModelCache =
            new CachedStoreFactory<ResolvedModuleVersionResult>("Resolved configuration");

    public ResolutionResultsStoreFactory(TemporaryFileProvider temp) {
        this.temp = temp;
    }

    private final ConcurrentMap<String, SimpleBinaryStore> stores = new MapMaker().makeMap();
    private final Object lock = new Object();

    public BinaryStore createBinaryStore(String id) {
        String storeKey = Thread.currentThread().getId() + id; //one store per thread
        if (stores.containsKey(storeKey)) {
            return stores.get(storeKey);
        }
        synchronized (lock) {
            SimpleBinaryStore store = stores.get(storeKey);
            if (store == null) {
                File storeFile = temp.createTemporaryFile("gradle", ".bin");
                storeFile.deleteOnExit();
                store = new SimpleBinaryStore(storeFile);
                stores.put(storeKey, store);
            }
            return store;
        }
    }

    public void close() throws IOException {
        Clock clock = new Clock();
        for (SimpleBinaryStore store : stores.values()) {
            store.close();
        }
        LOG.debug("Deleted {} resolution results binary files in {}", stores.size(), clock.getTime());
        oldModelCache.close();
        newModelCache.close();
    }

    public Store<TransientConfigurationResults> createOldModelCache(ConfigurationInternal configuration) {
        return oldModelCache.createCachedStore(configuration.getPath());
    }

    public Store<ResolvedModuleVersionResult> createNewModelCache(ConfigurationInternal configuration) {
        return newModelCache.createCachedStore(configuration.getPath());
    }

    private static class SimpleBinaryStore implements BinaryStore {
        private File file;
        private DataOutputStream outputStream;

        public SimpleBinaryStore(File file) {
            this.file = file;
            try {
                outputStream = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(file)));
            } catch (FileNotFoundException e) {
                throw throwAsUncheckedException(e);
            }
        }

        public DataOutputStream getOutput() {
            return outputStream;
        }

        public DataInputStream getInput() {
            try {
                return new DataInputStream(new BufferedInputStream(new FileInputStream(file)));
            } catch (FileNotFoundException e) {
                throw throwAsUncheckedException(e);
            }
        }

        public String diagnose() {
            return toString() + " (exist: " + file.exists() + ")";
        }

        @Override
        public String toString() {
            return "Binary store in " + file;
        }

        public void close() {
            try {
                outputStream.close();
            } catch (IOException e) {
                throw throwAsUncheckedException(e);
            }
            file.delete();
        }
    }
}
