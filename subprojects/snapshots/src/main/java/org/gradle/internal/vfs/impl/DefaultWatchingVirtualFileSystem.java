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

package org.gradle.internal.vfs.impl;

import com.google.common.collect.Interner;
import org.gradle.internal.file.Stat;
import org.gradle.internal.hash.FileHasher;
import org.gradle.internal.snapshot.CaseSensitivity;
import org.gradle.internal.vfs.WatchingVirtualFileSystem;

import java.nio.file.Path;
import java.util.Collections;

public class DefaultWatchingVirtualFileSystem extends DefaultVirtualFileSystem implements WatchingVirtualFileSystem {
    private final FileWatcherRegistry watcherRegistry;

    public DefaultWatchingVirtualFileSystem(
        FileHasher hasher,
        FileWatcherRegistry watcherRegistry,
        Interner<String> stringInterner,
        Stat stat,
        CaseSensitivity caseSensitivity,
        String... defaultExcludes
    ) {
        super(hasher, stringInterner, stat, caseSensitivity, defaultExcludes);
        this.watcherRegistry = watcherRegistry;
    }

    @Override
    public void startWatching() {
        root.get().visitKnownDirectories(directory -> watcherRegistry.watch(directory.toPath()));
    }

    @Override
    public void stopWatching() {
        watcherRegistry.stopAndProcess(new FileWatcherRegistry.ChangeEventHandler() {
            @Override
            public void handleEvent(FileWatcherRegistry.EventKind kind, Path path) {
                System.out.println("> Invalidating " + path);
                update(Collections.singleton(path.toString()), () -> {});
            }

            @Override
            public void handleOverflow() {
                System.out.println("> Invalidating entire VFS because there were too many changes");
                invalidateAll();
            }
        });
    }
}
