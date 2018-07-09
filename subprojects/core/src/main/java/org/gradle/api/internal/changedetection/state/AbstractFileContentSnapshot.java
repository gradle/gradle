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

package org.gradle.api.internal.changedetection.state;

import org.gradle.caching.internal.BuildCacheHasher;
import org.gradle.internal.hash.HashCode;

public abstract class AbstractFileContentSnapshot implements FileContentSnapshot {
    @Override
    public String getNormalizedPath() {
        return "";
    }

    @Override
    public FileContentSnapshot getSnapshot() {
        return this;
    }

    @Override
    public int compareTo(NormalizedFileSnapshot o) {
        if (!(o instanceof FileContentSnapshot)) {
            return -1;
        }
        return getContentMd5().compareTo(((FileContentSnapshot) o).getContentMd5());
    }

    @Override
    public void appendToHasher(BuildCacheHasher hasher) {
        hasher.putHash(getContentMd5());
    }

    @Override
    public HashCode getContentHash() {
        return getContentMd5();
    }
}
