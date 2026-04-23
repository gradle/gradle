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

import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;

import java.text.MessageFormat;
import java.util.concurrent.atomic.AtomicLong;

public interface FileHasherStatistics {
    /**
     * Number of files hashed.
     */
    long getHashedFileCount();

    /**
     * Amount of bytes hashed.
     */
    long getHashedContentLength();

    @ServiceScope(Scope.Global.class)
    class Collector {
        private final AtomicLong hashedFileCount = new AtomicLong();
        private final AtomicLong hashedContentLength = new AtomicLong();

        public void reportFileHashed(long length) {
            hashedFileCount.incrementAndGet();
            hashedContentLength.addAndGet(length);
        }

        public FileHasherStatistics collect() {
            long hashedFileCount = this.hashedFileCount.getAndSet(0);
            long hashedContentLength = this.hashedContentLength.getAndSet(0);
            return new FileHasherStatistics() {
                @Override
                public long getHashedFileCount() {
                    return hashedFileCount;
                }

                @Override
                public long getHashedContentLength() {
                    return hashedContentLength;
                }

                @Override
                public String toString() {
                    return MessageFormat.format("Hashed {0,number,integer} files ({1,number,integer} bytes)",
                        hashedFileCount, hashedContentLength
                    );
                }
            };
        }
    }
}
