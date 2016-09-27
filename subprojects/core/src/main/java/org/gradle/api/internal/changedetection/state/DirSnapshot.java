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

class DirSnapshot implements IncrementalFileSnapshot {
    private static final DirSnapshot INSTANCE = new DirSnapshot();
    private static final HashCode SIGNATURE = Hashing.md5().hashString(DirSnapshot.class.getName(), Charsets.UTF_8);

    private DirSnapshot() {
    }

    static DirSnapshot getInstance() {
        return INSTANCE;
    }

    @Override
    public boolean isContentAndMetadataUpToDate(IncrementalFileSnapshot snapshot) {
        return isContentUpToDate(snapshot);
    }

    public boolean isContentUpToDate(IncrementalFileSnapshot snapshot) {
        return snapshot instanceof DirSnapshot;
    }

    @Override
    public HashCode getHash() {
        return SIGNATURE;
    }

    @Override
    public String toString() {
        return "DIR";
    }
}
