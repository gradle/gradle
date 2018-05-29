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

import com.google.common.base.Preconditions;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public class MutablePhysicalDirectorySnapshot extends AbstractPhysicalDirectorySnapshot {
    private Map<String, PhysicalSnapshot> children = new LinkedHashMap<String, PhysicalSnapshot>();

    public MutablePhysicalDirectorySnapshot(Path path, String name) {
        super(path, name);
    }

    @Override
    protected Iterable<PhysicalSnapshot> getChildren() {
        return children.values();
    }

    @Override
    public PhysicalSnapshot add(String[] segments, int offset, PhysicalSnapshot snapshot) {
        if (segments.length == offset) {
            Preconditions.checkState(snapshot.getClass().equals(getClass()), "Expected different snapshot type: requested %s, but was: %s", snapshot.getClass().getSimpleName(), getClass().getSimpleName());
            return this;
        }
        String currentSegment = segments[offset];
        PhysicalSnapshot child = children.get(currentSegment);
        if (child == null) {
            if (segments.length == offset + 1) {
                child = snapshot;
            } else {
                child = new MutablePhysicalDirectorySnapshot(getPath().resolve(currentSegment), currentSegment);
            }
            children.put(currentSegment, child);
        }
        return child.add(segments, offset + 1, snapshot);
    }
}
