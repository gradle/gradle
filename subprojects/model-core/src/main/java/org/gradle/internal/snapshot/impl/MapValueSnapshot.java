/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.internal.snapshot.impl;

import com.google.common.collect.ImmutableList;
import org.gradle.internal.snapshot.ValueSnapshot;
import org.gradle.internal.snapshot.ValueSnapshotter;

public class MapValueSnapshot extends AbstractMapSnapshot<ValueSnapshot> implements ValueSnapshot {
    public MapValueSnapshot(ImmutableList<MapEntrySnapshot<ValueSnapshot>> entries) {
        super(entries);
    }

    @Override
    public ValueSnapshot snapshot(Object value, ValueSnapshotter snapshotter) {
        ValueSnapshot newSnapshot = snapshotter.snapshot(value);
        if (equals(newSnapshot)) {
            return this;
        }
        return newSnapshot;
    }
}
