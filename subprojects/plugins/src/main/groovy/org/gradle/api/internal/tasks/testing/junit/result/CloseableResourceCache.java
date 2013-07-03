/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.internal.tasks.testing.junit.result;

import org.gradle.api.Action;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.internal.UncheckedException;

import java.io.Closeable;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.gradle.internal.CompositeStoppable.stoppable;

/**
 * Manages a fixed size cache of resources that are derived from a key object.
 *
 * For example, a key may be a File where the resource is the FileOutputStream.
 * This class avoids having to open/close each write when scattering writes over a set of files.
 */
public class CloseableResourceCache<K, R extends Closeable> {

    private final static Logger LOG = Logging.getLogger(CloseableResourceCache.class);

    private final LinkedHashMap<K, R> cache;
    private final ResourceCreator<K, R> resourceCreator;

    public CloseableResourceCache(final int cacheSize, ResourceCreator<K, R> resourceCreator) {
        this.resourceCreator = resourceCreator;
        this.cache = new LinkedHashMap<K, R>(cacheSize, 1) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, R> eldest) {
                if (size() > cacheSize) {
                    close(eldest.getKey(), eldest.getValue());
                    return true;
                };

                return false;
            }
        };
    }

    public interface ResourceCreator<K, R extends Closeable> {
        R create(K key) throws Exception;
    }

    public void with(K key, Action<? super R> action) {
        R resource;
        try {
            if (cache.containsKey(key)) {
                resource = cache.get(key);
            } else {
                try {
                    resource = resourceCreator.create(key);
                } catch (Exception e) {
                    throw new RuntimeException("Error creating resource for key " + key, e);
                }

                cache.put(key, resource);
            }

            try {
                action.execute(resource);
            } catch (Exception e) {
                throw new RuntimeException("Error executing action with resource " + resource + " for key " + key, e);
            }
        } catch (RuntimeException e) {
            cleanUpQuietly();
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    public void closeAll() {
        try {
            for (Map.Entry<K, R> entry : cache.entrySet()) {
                close(entry.getKey(), entry.getValue());
            }
        } catch (UncheckedIOException e) {
            cleanUpQuietly();
            throw e;
        } finally {
            cache.clear();
        }
    }

    private void cleanUpQuietly() {
        try {
            stoppable(cache.values()).stop();
        } catch (Exception e) {
            LOG.debug("Problems closing resources", e);
        } finally {
            cache.clear();
        }
    }

    private void close(K key, R resource) {
        try {
            resource.close();
        } catch (IOException e) {
            throw new UncheckedIOException("Problems closing resource " + resource + " for key " + key);
        }
    }
}