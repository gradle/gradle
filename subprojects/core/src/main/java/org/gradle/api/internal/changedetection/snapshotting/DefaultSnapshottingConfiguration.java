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

package org.gradle.api.internal.changedetection.snapshotting;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import org.gradle.api.Action;
import org.gradle.api.internal.changedetection.resources.ResourceSnapshotter;
import org.gradle.api.snapshotting.SnapshotterConfiguration;
import org.gradle.api.snapshotting.internal.ResourceSnapshotterRegistry;
import org.gradle.internal.reflect.Instantiator;

import java.util.Map;

public class DefaultSnapshottingConfiguration implements SnapshottingConfigurationInternal {
    private final Map<Class<? extends SnapshotterConfiguration>, SnapshotterConfiguration> snapshotters;
    private final ResourceSnapshotterRegistry snapshotterRegistry;

    public DefaultSnapshottingConfiguration(ResourceSnapshotterRegistry snapshotterRegistry, Instantiator instantiator) {
        this.snapshotterRegistry = snapshotterRegistry;
        ImmutableMap.Builder<Class<? extends SnapshotterConfiguration>, SnapshotterConfiguration> builder = ImmutableMap.builder();
        for (Class<? extends SnapshotterConfiguration> snapshotterType : snapshotterRegistry.getConfigurationTypes()) {
            builder.put(snapshotterType, instantiator.newInstance(snapshotterType));
        }
        this.snapshotters = builder.build();
    }

    @Override
    public <T extends SnapshotterConfiguration> void snapshotter(Class<T> snapshotter, Action<T> configureAction) {
        configureAction.execute(Preconditions.checkNotNull(get(snapshotter), "Unknown snapshotter type " + snapshotter.getName()));
    }

    @Override
    public ResourceSnapshotter createSnapshotter(Class<? extends SnapshotterConfiguration> snapshotterType) {
        SnapshotterConfiguration configuration = get(snapshotterType);
        return snapshotterRegistry.createSnapshotter(configuration);
    }

    private <T extends SnapshotterConfiguration> T get(Class<T> snapshotterType) {
        return snapshotterType.cast(snapshotters.get(snapshotterType));
    }
}
