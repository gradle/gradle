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

import org.gradle.api.internal.changedetection.snapshotting.SnapshottingConfigurationInternal;
import org.gradle.internal.reflect.Instantiator;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static org.gradle.internal.Cast.uncheckedCast;

public class DefaultFileCollectionSnapshotterRegistry implements FileCollectionSnapshotterRegistry {
    private final Map<Class<? extends FileCollectionSnapshotter>, Class<? extends FileCollectionSnapshotter>> snapshotterTypes;
    private final Instantiator instantiator;
    private final ConcurrentMap<SnapshotterKey, FileCollectionSnapshotter> snapshotters;

    public DefaultFileCollectionSnapshotterRegistry(Map<Class<? extends FileCollectionSnapshotter>, Class<? extends FileCollectionSnapshotter>> snapshotterTypes, Instantiator instantiator) {
        this.snapshotterTypes = snapshotterTypes;
        this.instantiator = instantiator;
        this.snapshotters = new ConcurrentHashMap<SnapshotterKey, FileCollectionSnapshotter>();
    }

    @Override
    public <T> T getSnapshotter(Class<? extends T> type, SnapshottingConfigurationInternal configuration) {
        SnapshotterKey snapshotterKey = new SnapshotterKey(type, configuration);
        FileCollectionSnapshotter snapshotter = snapshotters.get(snapshotterKey);
        if (snapshotter == null) {
            snapshotter = createSnapshotter(type, configuration, snapshotterKey);
        }
        return uncheckedCast(snapshotter);
    }

    private <T> FileCollectionSnapshotter createSnapshotter(Class<? extends T> type, SnapshottingConfigurationInternal configuration, SnapshotterKey snapshotterKey) {
        @SuppressWarnings("SuspiciousMethodCalls")
        Class<? extends FileCollectionSnapshotter> implementationType = snapshotterTypes.get(type);
        if (implementationType == null) {
            throw new IllegalStateException(String.format("No snapshotter registered with type '%s'", type.getName()));
        }
        FileCollectionSnapshotter snapshotter = instantiator.newInstance(implementationType, configuration);
        FileCollectionSnapshotter oldSnapshotter = snapshotters.putIfAbsent(snapshotterKey, snapshotter);
        if (oldSnapshotter != null) {
            return oldSnapshotter;
        }
        return snapshotter;
    }

    private static class SnapshotterKey {
        private final Class<?> type;
        private final SnapshottingConfigurationInternal configuration;

        private SnapshotterKey(Class<?> type, SnapshottingConfigurationInternal configuration) {
            this.type = type;
            this.configuration = configuration;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            SnapshotterKey that = (SnapshotterKey) o;

            if (!type.equals(that.type)) {
                return false;
            }
            return configuration.equals(that.configuration);
        }

        @Override
        public int hashCode() {
            int result = type.hashCode();
            result = 31 * result + configuration.hashCode();
            return result;
        }
    }
}
