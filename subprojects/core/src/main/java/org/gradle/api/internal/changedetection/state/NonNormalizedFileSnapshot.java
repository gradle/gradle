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

import org.gradle.api.internal.changedetection.state.mirror.PhysicalSnapshot;
import org.gradle.internal.file.FileType;
import org.gradle.internal.hash.HashCode;

public class NonNormalizedFileSnapshot extends AbstractNormalizedFileSnapshot {
    private final String absolutePath;

    public NonNormalizedFileSnapshot(String absolutePath, FileType type, HashCode contentHash) {
        super(type, contentHash);
        this.absolutePath = absolutePath;
    }

    public NonNormalizedFileSnapshot(PhysicalSnapshot snapshot) {
        this(snapshot.getAbsolutePath(), snapshot.getType(), snapshot.getContent().getContentMd5());
    }

    @Override
    public String getNormalizedPath() {
        return absolutePath;
    }
}
