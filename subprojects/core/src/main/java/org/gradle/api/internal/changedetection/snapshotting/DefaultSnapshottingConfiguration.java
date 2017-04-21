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
import org.gradle.api.Action;
import org.gradle.api.snapshotting.Snapshotter;
import org.gradle.internal.reflect.Instantiator;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DefaultSnapshottingConfiguration implements SnapshottingConfigurationInternal {
    private final Map<Class<? extends Snapshotter>, Snapshotter> snapshotters;

    public DefaultSnapshottingConfiguration(List<Class<? extends Snapshotter>> snapshotterTypes, Instantiator instantiator) {
        snapshotters = new HashMap<Class<? extends Snapshotter>, Snapshotter>(snapshotterTypes.size());
        for (Class<? extends Snapshotter> snapshotterType : snapshotterTypes) {
            snapshotters.put(snapshotterType, instantiator.newInstance(snapshotterType));
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends Snapshotter> T get(Class<T> snapshotterType) {
        return (T) snapshotters.get(snapshotterType);
    }

    @Override
    public <T extends Snapshotter> void snapshotter(Class<T> snapshotter, Action<T> configureAction) {
        configureAction.execute(Preconditions.checkNotNull(get(snapshotter), "Unknown snapshotter type " + snapshotter.getName()));
    }
}
