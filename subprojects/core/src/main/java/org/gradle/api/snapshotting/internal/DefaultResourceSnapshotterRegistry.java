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

package org.gradle.api.snapshotting.internal;

import com.google.common.collect.ImmutableMap;
import org.gradle.api.internal.changedetection.resources.ResourceSnapshotter;
import org.gradle.api.snapshotting.SnapshotterConfiguration;
import org.gradle.internal.Cast;

import java.util.Map;
import java.util.Set;

public class DefaultResourceSnapshotterRegistry implements ResourceSnapshotterRegistry {
    private final Map<Class<? extends SnapshotterConfiguration>, ResourceSnapshotterFactory<?>> factories;

    public DefaultResourceSnapshotterRegistry(Map<Class<? extends SnapshotterConfiguration>, ResourceSnapshotterFactory<?>> factories) {
        this.factories = ImmutableMap.copyOf(factories);
    }

    @Override
    public <T extends SnapshotterConfiguration> ResourceSnapshotter createSnapshotter(T configuration) {
        Class<? extends SnapshotterConfiguration> configurationType = configuration.getClass();
        for (Map.Entry<Class<? extends SnapshotterConfiguration>, ResourceSnapshotterFactory<?>> entry : factories.entrySet()) {
            Class<? extends SnapshotterConfiguration> type = entry.getKey();
            if (type.isAssignableFrom(configurationType)) {
                ResourceSnapshotterFactory<? super T> value = Cast.uncheckedCast(entry.getValue());
                return value.create(configuration);
            }
        }
        throw new IllegalStateException("Unknown snapshotter configuration type: " + configurationType.getName());
    }

    @Override
    public Set<Class<? extends SnapshotterConfiguration>> getConfigurationTypes() {
        return factories.keySet();
    }
}
