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

import com.google.common.collect.ImmutableList;
import org.gradle.api.snapshotting.Snapshotter;

import java.util.List;

public class SnapshotterCacheKey {
    public List<Snapshotter> getConfigurations() {
        return configurations;
    }

    private final List<Snapshotter> configurations;

    public Class<?> getSnapshotterClass() {
        return snapshotterClass;
    }

    private final Class<?> snapshotterClass;

    public SnapshotterCacheKey(Class<?> snapshotterClass, Snapshotter... configurations) {
        this.configurations = ImmutableList.copyOf(configurations);
        this.snapshotterClass = snapshotterClass;
    }
}
