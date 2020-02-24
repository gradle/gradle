/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.internal.vfs;

import com.google.common.collect.Iterables;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.snapshot.CompleteFileSystemLocationSnapshot;
import org.gradle.internal.snapshot.SnapshottingFilter;

import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;

public class RoutingVirtualFileSystem implements VirtualFileSystem {
    private final AdditiveCacheLocations additiveCacheLocations;
    private final VirtualFileSystem gradleUserHomeVirtualFileSystem;
    private final VirtualFileSystem buildScopedVirtualFileSystem;
    private final BooleanSupplier vfsRetained;

    public RoutingVirtualFileSystem(
        AdditiveCacheLocations additiveCacheLocations,
        VirtualFileSystem gradleUserHomeVirtualFileSystem,
        VirtualFileSystem buildScopedVirtualFileSystem,
        BooleanSupplier vfsRetained
    ) {
        this.additiveCacheLocations = additiveCacheLocations;
        this.gradleUserHomeVirtualFileSystem = gradleUserHomeVirtualFileSystem;
        this.buildScopedVirtualFileSystem = buildScopedVirtualFileSystem;
        this.vfsRetained = vfsRetained;
    }

    @Override
    public <T> T read(String location, Function<CompleteFileSystemLocationSnapshot, T> visitor) {
        return getVirtualFileSystemFor(location).read(location, visitor);
    }

    @Override
    public <T> Optional<T> readRegularFileContentHash(String location, Function<HashCode, T> visitor) {
        return getVirtualFileSystemFor(location).readRegularFileContentHash(location, visitor);
    }

    @Override
    public void read(String location, SnapshottingFilter filter, Consumer<CompleteFileSystemLocationSnapshot> visitor) {
        getVirtualFileSystemFor(location).read(location, filter, visitor);
    }

    @Override
    public void update(Iterable<String> locations, Runnable action) {
        if (Iterables.isEmpty(locations)) {
            return;
        }
        if (vfsRetained.getAsBoolean()) {
            gradleUserHomeVirtualFileSystem.update(locations, action);
        } else {
            Iterable<String> immutableLocations = Iterables.filter(locations, additiveCacheLocations::isInsideAdditiveCache);
            int immutableLocationsSize = Iterables.size(immutableLocations);
            if (immutableLocationsSize == 0) {
                buildScopedVirtualFileSystem.update(locations, action);
            } else if (immutableLocationsSize == Iterables.size(locations)) {
                gradleUserHomeVirtualFileSystem.update(locations, action);
            } else {
                Iterable<String> mutableLocations = Iterables.filter(locations, location -> !additiveCacheLocations.isInsideAdditiveCache(location));
                gradleUserHomeVirtualFileSystem.update(immutableLocations, action);
                buildScopedVirtualFileSystem.update(mutableLocations, action);
            }
        }
    }

    @Override
    public void invalidateAll() {
        if (vfsRetained.getAsBoolean()) {
            gradleUserHomeVirtualFileSystem.invalidateAll();
        } else {
            buildScopedVirtualFileSystem.invalidateAll();
        }
    }

    @Override
    public void updateWithKnownSnapshot(CompleteFileSystemLocationSnapshot snapshot) {
        getVirtualFileSystemFor(snapshot.getAbsolutePath()).updateWithKnownSnapshot(snapshot);
    }

    private VirtualFileSystem getVirtualFileSystemFor(String location) {
        return vfsRetained.getAsBoolean()
            ? gradleUserHomeVirtualFileSystem
            : additiveCacheLocations.isInsideAdditiveCache(location)
                ? gradleUserHomeVirtualFileSystem
                : buildScopedVirtualFileSystem;
    }
}
