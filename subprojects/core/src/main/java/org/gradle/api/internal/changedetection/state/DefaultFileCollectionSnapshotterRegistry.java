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

import com.google.common.base.Function;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;

import java.util.Collection;
import java.util.Map;

import static org.gradle.internal.Cast.uncheckedCast;

public class DefaultFileCollectionSnapshotterRegistry implements FileCollectionSnapshotterRegistry {
    private final Map<Class<?>, FileCollectionSnapshotter> snapshotters;

    public DefaultFileCollectionSnapshotterRegistry(Collection<FileCollectionSnapshotter> snapshotters) {
        this.snapshotters = ImmutableMap.copyOf(Maps.uniqueIndex(snapshotters, new Function<FileCollectionSnapshotter, Class<?>>() {
            @Override
            public Class<?> apply(FileCollectionSnapshotter snapshotter) {
                Class<? extends FileCollectionSnapshotter> registeredType = snapshotter.getRegisteredType();
                Class<? extends FileCollectionSnapshotter> type = snapshotter.getClass();
                if (!registeredType.isAssignableFrom(type)) {
                    throw new IllegalArgumentException(String.format("Snapshotter registered type '%s' must be a super-type of the actual snapshotter type '%s'", registeredType.getName(), type.getName()));
                }
                return registeredType;
            }
        }));
    }

    @Override
    public Collection<FileCollectionSnapshotter> getAllSnapshotters() {
        return snapshotters.values();
    }

    @Override
    public <T> T getSnapshotter(Class<? extends T> type) {
        FileCollectionSnapshotter snapshotter = snapshotters.get(type);
        if (snapshotter == null) {
            throw new IllegalStateException(String.format("No snapshotter registered with type '%s'", type.getName()));
        }
        return uncheckedCast(snapshotter);
    }
}
