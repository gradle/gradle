/*
 * Copyright 2016 the original author or authors.
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

import com.google.common.base.Charsets;
import com.google.common.hash.HashCode;
import com.google.common.hash.Hashing;
import org.gradle.internal.nativeintegration.filesystem.FileType;

class MissingFileSnapshot implements IncrementalFileSnapshot {
    private static final MissingFileSnapshot INSTANCE = new MissingFileSnapshot();
    private static final HashCode SIGNATURE = Hashing.md5().hashString(MissingFileSnapshot.class.getName(), Charsets.UTF_8);

    private MissingFileSnapshot() {
    }

    static MissingFileSnapshot getInstance() {
        return INSTANCE;
    }

    @Override
    public boolean isContentAndMetadataUpToDate(IncrementalFileSnapshot snapshot) {
        return isContentUpToDate(snapshot);
    }

    public boolean isContentUpToDate(IncrementalFileSnapshot snapshot) {
        return snapshot instanceof MissingFileSnapshot;
    }

    @Override
    public FileType getType() {
        return FileType.Missing;
    }

    @Override
    public HashCode getContentMd5() {
        return SIGNATURE;
    }

    @Override
    public String toString() {
        return "MISSING";
    }
}
