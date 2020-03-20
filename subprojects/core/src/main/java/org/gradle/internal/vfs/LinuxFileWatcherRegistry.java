/*
 * Copyright 2020 the original author or authors.
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

import com.google.common.collect.HashMultiset;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multiset;
import net.rubygrapefruit.platform.Native;
import net.rubygrapefruit.platform.NativeException;
import net.rubygrapefruit.platform.internal.jni.LinuxFileEventFunctions;
import org.gradle.internal.snapshot.CompleteDirectorySnapshot;
import org.gradle.internal.snapshot.CompleteFileSystemLocationSnapshot;
import org.gradle.internal.snapshot.FileSystemSnapshotVisitor;
import org.gradle.internal.vfs.watch.FileWatcherRegistry;
import org.gradle.internal.vfs.watch.FileWatcherRegistryFactory;
import org.gradle.internal.vfs.watch.WatchRootUtil;
import org.gradle.internal.vfs.watch.WatchingNotSupportedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class LinuxFileWatcherRegistry extends AbstractEventDrivenFileWatcherRegistry {
    private static final Logger LOGGER = LoggerFactory.getLogger(LinuxFileWatcherRegistry.class);

    private final Multiset<String> watchedRoots = HashMultiset.create();
    private final Set<String> mustWatchDirectories = new HashSet<>();
    private final Map<String, ImmutableList<String>> watchedRootsForSnapshot = new HashMap<>();

    public LinuxFileWatcherRegistry(Predicate<String> watchFilter, ChangeHandler handler) {
        super(
            callback -> Native.get(LinuxFileEventFunctions.class).startWatcher(callback),
            watchFilter,
            handler
        );
    }

    @Override
    protected void handleChanges(Collection<CompleteFileSystemLocationSnapshot> removedSnapshots, Collection<CompleteFileSystemLocationSnapshot> addedSnapshots) {
        Map<String, Integer> changedWatchedDirectories = new HashMap<>();

        removedSnapshots.forEach(snapshot -> {
            ImmutableList<String> previousWatchedRoots = watchedRootsForSnapshot.remove(snapshot.getAbsolutePath());
            previousWatchedRoots.forEach(path -> decrement(path, changedWatchedDirectories));
            snapshot.accept(new OnlyVisitSubDirectories(path -> decrement(path, changedWatchedDirectories)));
        });
        addedSnapshots.forEach(snapshot -> {
            ImmutableList<String> directoriesToWatchForRoot = ImmutableList.copyOf(WatchRootUtil.getDirectoriesToWatch(snapshot).stream()
                .map(Path::toString).collect(Collectors.toList()));
            watchedRootsForSnapshot.put(snapshot.getAbsolutePath(), directoriesToWatchForRoot);
            directoriesToWatchForRoot.forEach(path -> increment(path, changedWatchedDirectories));
            snapshot.accept(new OnlyVisitSubDirectories(path -> increment(path, changedWatchedDirectories)));
        });
        updateWatchedDirectories(changedWatchedDirectories);
    }

    @Override
    public void updateMustWatchDirectories(Collection<File> updatedWatchDirectories) {
        Map<String, Integer> changedDirectories = new HashMap<>();
        mustWatchDirectories.forEach(path -> decrement(path, changedDirectories));
        mustWatchDirectories.clear();
        updatedWatchDirectories.stream()
            .filter(File::isDirectory)
            .map(File::getAbsolutePath)
            .forEach(mustWatchDirectories::add);
        mustWatchDirectories.forEach(path -> increment(path, changedDirectories));
        updateWatchedDirectories(changedDirectories);

    }

    private void updateWatchedDirectories(Map<String, Integer> changedWatchDirectories) {
        if (changedWatchDirectories.isEmpty()) {
            return;
        }
        Set<File> watchRootsToRemove = new HashSet<>();
        Set<File> watchRootsToAdd = new HashSet<>();
        changedWatchDirectories.forEach((absolutePath, value) -> {
            int count = value;
            if (count < 0) {
                int toRemove = -count;
                int contained = watchedRoots.remove(absolutePath, toRemove);
                if (contained <= toRemove) {
                    watchRootsToRemove.add(new File(absolutePath));
                }
            } else if (count > 0) {
                int contained = watchedRoots.add(absolutePath, count);
                if (contained == 0) {
                    watchRootsToAdd.add(new File(absolutePath));
                }
            }
        });
        if (watchedRoots.isEmpty()) {
            LOGGER.info("Not watching anything anymore");
        }
        LOGGER.info("Watching {} directory hierarchies to track changes", watchedRoots.entrySet().size());
        try {
            getWatcher().stopWatching(watchRootsToRemove);
            getWatcher().startWatching(watchRootsToAdd);
        } catch (NativeException e) {
            if (e.getMessage().contains("Already watching path: ")) {
                throw new WatchingNotSupportedException("Unable to watch same file twice via different paths: " + e.getMessage(), e);
            }
            throw e;
        }
    }

    private static void decrement(String path, Map<String, Integer> changedWatchedDirectories) {
        changedWatchedDirectories.compute(path, (key, value) -> value == null ? -1 : value - 1);
    }

    private static void increment(String path, Map<String, Integer> changedWatchedDirectories) {
        changedWatchedDirectories.compute(path, (key, value) -> value == null ? 1 : value + 1);
    }

    public static class Factory implements FileWatcherRegistryFactory {
        @Override
        public FileWatcherRegistry startWatcher(Predicate<String> watchFilter, ChangeHandler handler) {
            return new LinuxFileWatcherRegistry(watchFilter, handler);
        }
    }

    private static class OnlyVisitSubDirectories implements FileSystemSnapshotVisitor {
        private final Consumer<String> subDirectoryConsumer;
        boolean root;

        public OnlyVisitSubDirectories(Consumer<String> subDirectoryConsumer) {
            this.subDirectoryConsumer = subDirectoryConsumer;
            root = true;
        }

        @Override
        public boolean preVisitDirectory(CompleteDirectorySnapshot directorySnapshot) {
            if (!root) {
                subDirectoryConsumer.accept(directorySnapshot.getAbsolutePath());
            }
            root = false;
            return true;
        }

        @Override
        public void visitFile(CompleteFileSystemLocationSnapshot fileSnapshot) {
        }

        @Override
        public void postVisitDirectory(CompleteDirectorySnapshot directorySnapshot) {
        }
    }
}
