/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.caching.internal.version2;

import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.Lists;
import com.google.common.io.ByteStreams;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.gradle.api.UncheckedIOException;
import org.gradle.caching.internal.OutputPropertySpec;
import org.gradle.internal.hash.HashCode;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveTask;

@SuppressWarnings("Since15")
public class DefaultBuildCacheControllerV2 implements BuildCacheControllerV2 {
    private final ForkJoinPool operations = new ForkJoinPool();
    private final LocalBuildCacheServiceV2 local;

    public DefaultBuildCacheControllerV2(LocalBuildCacheServiceV2 local) {
        this.local = local;
    }

    @Override
    public <T> T load(final BuildCacheLoadCommandV2<T> command) {
        return operations.invoke(new RecursiveTask<T>() {
            @Override
            protected T compute() {
                GetEntry loadResult = new GetEntry(HashCode.fromString(command.getKey().getHashCode()));
                invokeAll(loadResult);
                CacheEntry entry;
                try {
                    entry = loadResult.get();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                } catch (ExecutionException e) {
                    throw new RuntimeException(e);
                }
                if (entry instanceof ResultEntry) {
                    // We have a hit
                    ResultEntry result = (ResultEntry) entry;
                    Map<String, HashCode> outputs = result.getOutputs();
                    List<LoadEntry> childEntries = Lists.newArrayListWithCapacity(outputs.size());
                    for (OutputPropertySpec propertySpec : command.getOutputProperties()) {
                        HashCode outputKey = outputs.get(propertySpec.getPropertyName());
                        if (outputKey == null) {
                            // Optional outputs are missing
                            FileUtils.deleteQuietly(propertySpec.getOutputRoot());
                            continue;
                        }
                        childEntries.add(new LoadEntry(outputKey, propertySpec.getOutputRoot()));
                    }
                    invokeAll(childEntries);
                    return command.parseOriginMetadata(result.getOriginMetadata());
                } else if (entry != null) {
                    throw new IllegalStateException("Found an entry of unknown type for " + command.getKey());
                } else {
                    return null;
                }
            }
        });
    }

    @Override
    public void store(BuildCacheStoreCommandV2 command) {

    }

    class GetEntry extends RecursiveTask<CacheEntry> {
        protected final HashCode key;

        public GetEntry(HashCode key) {
            this.key = key;
        }

        @Override
        protected CacheEntry compute() {
            try {
                return load();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        protected CacheEntry load() throws IOException {
            CacheEntry entry = local.get(key);
//            if (entry == null) {
//                // This stores it in local, too
//                entry = remote.get(entry);
//            }
            return entry;
        }
    }

    class LoadEntry extends GetEntry {
        private File target;

        public LoadEntry(HashCode key, File target) {
            super(key);
            this.target = target;
        }

        @Override
        protected CacheEntry load() throws IOException {
            CacheEntry entry = super.load();
            if (entry instanceof FileEntry) {
                InputStream inputStream = ((FileEntry) entry).read();
                try {
                    FileUtils.deleteQuietly(target);
                    OutputStream outputStream = new FileOutputStream(target);
                    try {
                        ByteStreams.copy(inputStream, outputStream);
                    } finally {
                        IOUtils.closeQuietly(outputStream);
                    }
                } finally {
                    IOUtils.closeQuietly(inputStream);
                }
            } else if (entry instanceof ManifestEntry) {
                ManifestEntry manifest = (ManifestEntry) entry;
                ImmutableSortedMap<String, HashCode> childEntries = manifest.getChildren();
                FileUtils.deleteQuietly(target);
                FileUtils.forceMkdir(target);
                List<LoadEntry> childTasks = Lists.newArrayListWithCapacity(childEntries.size());
                for (Map.Entry<String, HashCode> childEntry : childEntries.entrySet()) {
                    childTasks.add(new LoadEntry(childEntry.getValue(), new File(target, childEntry.getKey())));
                }
                invokeAll(childTasks);
            } else if (entry == null) {
                throw new IllegalStateException("Entry not found: " + key);
            } else {
                throw new IllegalStateException("Invalid entry type: " + entry.getClass().getName());
            }
            return entry;
        }
    }
}
