/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.api.internal.changedetection.state;

import java.text.MessageFormat;
import java.util.concurrent.atomic.AtomicLong;

public interface CachingFileHasherStatistics {
    /**
     * Number of files actually hashed.
     */
    long getHashedFileCount();

    /**
     * Amount of bytes actually hashed.
     */
    long getHashedContentLength();

    /**
     * Number of files not hashed but served from cache.
     */
    long getCachedFileCount();

    /**
     * Amount of bytes not hashed but served a hash for from cache.
     */
    long getCachedContentLength();

    class Collector {
        private final AtomicLong hashedFileCount = new AtomicLong();
        private final AtomicLong hashedContentLength = new AtomicLong();
        private final AtomicLong cachedFileCount = new AtomicLong();
        private final AtomicLong cachedContentLength = new AtomicLong();

        public void reportFileHashRequested(long length, boolean servedFromCache) {
            if (servedFromCache) {
                cachedFileCount.incrementAndGet();
                cachedContentLength.addAndGet(length);
            } else {
                hashedFileCount.incrementAndGet();
                hashedContentLength.addAndGet(length);
            }
        }

        public CachingFileHasherStatistics collect() {
            long totalHashedFileCount = hashedFileCount.getAndSet(0);
            long totalHashedContentLength = hashedContentLength.getAndSet(0);
            long totalCachedFileCount = cachedFileCount.getAndSet(0);
            long totalCachedContentLength = cachedContentLength.getAndSet(0);
            return new CachingFileHasherStatistics() {
                @Override
                public long getHashedFileCount() {
                    return totalHashedFileCount;
                }

                @Override
                public long getHashedContentLength() {
                    return totalHashedContentLength;
                }

                @Override
                public long getCachedFileCount() {
                    return totalCachedFileCount;
                }

                @Override
                public long getCachedContentLength() {
                    return totalCachedContentLength;
                }

                @Override
                public String toString() {
                    return MessageFormat.format("Hashed {0,number,integer} files ({1,number} kB), served hash from cache for {2,number,integer} files ({3,number} kB)",
                        totalHashedFileCount,
                        totalHashedContentLength / 1024D,
                        totalCachedFileCount,
                        totalCachedContentLength / 1024D
                    );
                }
            };
        }

    }
}
