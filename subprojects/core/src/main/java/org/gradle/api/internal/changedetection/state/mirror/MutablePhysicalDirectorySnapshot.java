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

package org.gradle.api.internal.changedetection.state.mirror;

import org.gradle.api.internal.cache.StringInterner;
import org.gradle.internal.hash.HashCode;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

public class MutablePhysicalDirectorySnapshot extends AbstractPhysicalDirectorySnapshot implements MutablePhysicalSnapshot {
    private final StringInterner stringInterner;
    private Map<String, MutablePhysicalSnapshot> children = new LinkedHashMap<String, MutablePhysicalSnapshot>();

    public MutablePhysicalDirectorySnapshot(String absolutePath, String name, StringInterner stringInterner) {
        super(absolutePath, name);
        this.stringInterner = stringInterner;
    }

    @Override
    public Iterable<MutablePhysicalSnapshot> getChildren() {
        return children.values();
    }

    @Override
    public HashCode getContentHash() {
        return SIGNATURE;
    }

    @Override
    public MutablePhysicalSnapshot add(String[] segments, int offset, MutablePhysicalSnapshot snapshot) {
        if (segments.length == offset) {
            if (!snapshot.getClass().equals(getClass())) {
                throw new IllegalStateException(String.format("Expected different snapshot type: requested %s, but was: %s", snapshot.getClass().getSimpleName(), getClass().getSimpleName()));
            }
            return this;
        }
        String currentSegment = segments[offset];
        MutablePhysicalSnapshot child = children.get(currentSegment);
        if (child == null) {
            if (segments.length == offset + 1) {
                child = snapshot;
            } else {
                child = new MutablePhysicalDirectorySnapshot(stringInterner.intern(getAbsolutePath() + File.separatorChar + currentSegment), currentSegment, stringInterner);
            }
            children.put(currentSegment, child);
        }
        return child.add(segments, offset + 1, snapshot);
    }
}
