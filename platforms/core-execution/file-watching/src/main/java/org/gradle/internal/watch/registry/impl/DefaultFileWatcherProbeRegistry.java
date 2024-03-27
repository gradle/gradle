/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.internal.watch.registry.impl;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.collect.Streams;
import com.google.common.primitives.Longs;
import org.gradle.internal.watch.registry.FileWatcherProbeRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

public class DefaultFileWatcherProbeRegistry implements FileWatcherProbeRegistry {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultFileWatcherProbeRegistry.class);

    private final Map<FileStore, WatchProbeImpl> probesByFS = new ConcurrentHashMap<>();
    private final Map<Path, WatchProbeImpl> watchProbesByHierarchy = new ConcurrentHashMap<>();
    private final Set<File> unwatchedHierarchies = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private ImmutableSet<File> probedHierarchies = ImmutableSet.of();

    @Override
    public void registerProbe(File watchableHierarchy, File probeLocation) {
        LOGGER.debug("Registering probe for {}", watchableHierarchy);
        File probeFile = new File(probeLocation, "file-system.probe");

        FileStore fileStore = getFileStore(watchableHierarchy.toPath());
        WatchProbeImpl watchProbe;
        if (fileStore != null) {
            watchProbe = probesByFS.computeIfAbsent(fileStore, k -> new WatchProbeImpl(probeFile, isSubpath(probeFile, watchableHierarchy)));
        } else { // fallback, unlikely to happen
            watchProbe = watchProbesByHierarchy
                .values()
                .stream()
                .filter(probe -> probe.probeFile.equals(probeFile))
                .findFirst()
                .orElseGet(() -> new WatchProbeImpl(probeFile, isSubpath(probeFile, watchableHierarchy)));

            watchProbesByHierarchy.put(watchableHierarchy.toPath(), watchProbe);
        }

        watchProbe.addWatchableHierarchy(watchableHierarchy);
    }

    private static boolean isSubpath(File probeFile, File watchableHierarchy) {
        return probeFile.toPath().startsWith(watchableHierarchy.toPath());
    }

    @Override
    public void updateProbedHierarchies(ImmutableSet<File> probedHierarchies, BiConsumer<File, Boolean> probeDisarmed, BiConsumer<File, Boolean> beforeProbeArmed) {
        if (this.probedHierarchies.equals(probedHierarchies)) {
            return;
        }

        Set<File> removedHierarchies = Sets.difference(this.probedHierarchies, probedHierarchies);
        stopWatching(removedHierarchies.stream(), probeDisarmed);

        Set<File> addedHierarchies = Sets.difference(probedHierarchies, this.probedHierarchies);
        startWatching(addedHierarchies.stream(), beforeProbeArmed);

        this.probedHierarchies = probedHierarchies;
    }

    private void stopWatching(Stream<File> hierarchies, BiConsumer<File, Boolean> probeDisarmed) {
        hierarchies
            .map(hierarchy -> {
                unwatchedHierarchies.add(hierarchy);
                return getProbeForHierarchy(hierarchy.toPath());
            })
            .filter(Objects::nonNull)
            .distinct()
            .forEach(probe -> {
                if (!probe.hasWatchableHierarchies(unwatchedHierarchies)) {
                    probe.disarm();
                    probeDisarmed.accept(probe.getLocation(), probe.isSubPathOfWatchableHierarchy());
                }
            });
    }

    private void startWatching(Stream<File> hierarchies, BiConsumer<File, Boolean> beforeProbeArmed) {
        hierarchies
            .map(File::toPath)
            .map(this::getProbeForHierarchy)
            .filter(Objects::nonNull)
            .filter(x -> x.state == WatchProbeImpl.State.UNARMED)
            .distinct()
            .forEach(probe -> {
                beforeProbeArmed.accept(probe.getLocation(), probe.isSubPathOfWatchableHierarchy());
                probe.arm();
            });
    }

    @Override
    public Stream<File> unprovenHierarchies() {
        return Streams.concat(
                probesByFS.values().stream(),
                watchProbesByHierarchy.values().stream()
            )
            .filter(WatchProbeImpl::leftArmed)
            .flatMap(watchProbe -> watchProbe.getWatchableHierarchies(unwatchedHierarchies));
    }

    /**
     * Triggers a watch probe at the given location if one exists.
     */
    @Override
    public void triggerWatchProbe(Path path) {
        WatchProbeImpl probe = getProbeForHierarchy(path);
        if (probe != null) {
            LOGGER.debug("Triggering watch probe for {}", probe.getWatchableHierarchies(unwatchedHierarchies));
            probe.trigger();
        }
    }


    // can be null if watchableHierarchy is moved
    @Nullable
    private WatchProbeImpl getProbeForHierarchy(Path watchableHierarchy) {
        FileStore fileStore = getFileStore(watchableHierarchy);
        WatchProbeImpl watchProbe;
        if (fileStore != null) {
            watchProbe = probesByFS.get(fileStore);
        } else {
            watchProbe = watchProbesByHierarchy.get(watchableHierarchy);
        }
        if (watchProbe == null) {
            LOGGER.debug("Did not find watchable hierarchy probe for: {}", watchableHierarchy);
        }
        return watchProbe;
    }

    @Nullable
    private static FileStore getFileStore(Path file) {
        try {
            return Files.getFileStore(file);
        } catch (FileNotFoundException e) {
            LOGGER.debug("Could not detect file system for hierarchy because it does not exists: {}", file, e);
            return null;
        } catch (IOException e) {
            LOGGER.debug("Could not detect file system for hierarchy: {}", file, e);
            return null;
        }
    }

    private static class WatchProbeImpl {
        public enum State {
            /**
             * Probe hasn't been armed yet.
             */
            UNARMED,

            /**
             * Probe file exists, waiting for event to arrive.
             */
            ARMED,

            /**
             * The expected event has arrived.
             */
            TRIGGERED
        }

        private final Set<File> watchableHierarchies;
        private final File probeFile;
        private final boolean subPathOfWatchableHierarchy;
        private State state = State.UNARMED;

        public WatchProbeImpl(File probeFile, boolean subPathOfWatchableHierarchy) {
            this.probeFile = probeFile;
            this.subPathOfWatchableHierarchy = subPathOfWatchableHierarchy;
            this.watchableHierarchies = new HashSet<>();
        }

        public File getLocation() {
            return probeFile.getParentFile();
        }

        public boolean isSubPathOfWatchableHierarchy() {
            return subPathOfWatchableHierarchy;
        }

        public synchronized void arm() {
            switch (state) {
                case UNARMED:
                    state = State.ARMED;
                    //noinspection ResultOfMethodCallIgnored
                    probeFile.getParentFile().mkdirs();
                    try (FileOutputStream out = new FileOutputStream(probeFile)) {
                        out.write(Longs.toByteArray(System.currentTimeMillis()));
                    } catch (IOException e) {
                        LOGGER.debug("Could not arm watch probe for hierarchies: {}", watchableHierarchies, e);
                        return;
                    }
                    LOGGER.debug("Watch probe has been armed for hierarchies: {}", watchableHierarchies);
                    break;
                case ARMED:
                    LOGGER.debug("Watch probe for hierarchies is already armed: {}", watchableHierarchies);
                    break;
                case TRIGGERED:
                    LOGGER.debug("Watch probe for hierarchies has already been triggered: {}", watchableHierarchies);
                    break;
                default:
                    throw new AssertionError();
            }
        }

        public synchronized void disarm() {
            switch (state) {
                case UNARMED:
                    LOGGER.debug("Watch probe has already been disarmed for hierarchies: {}", watchableHierarchies);
                    break;
                case ARMED:
                    state = State.UNARMED;
                    LOGGER.debug("Watch probe has been disarmed for hierarchies: {}", watchableHierarchies);
                    break;
                case TRIGGERED:
                    LOGGER.debug("Watch probe has already been triggered for hierarchies: {}", watchableHierarchies);
                    break;
            }
        }

        public synchronized void trigger() {
            if (state != State.TRIGGERED) {
                LOGGER.debug("Watch probe in state {} has been triggered for hierarchies: {}", state, watchableHierarchies);
                state = State.TRIGGERED;
            }
        }

        public synchronized boolean leftArmed() {
            return state == State.ARMED;
        }

        public Stream<File> getWatchableHierarchies(Set<File> unwatchedHierarchies) {
            return Sets.difference(watchableHierarchies, unwatchedHierarchies).stream();
        }

        public void addWatchableHierarchy(File watchableHierarchy) {
            watchableHierarchies.add(watchableHierarchy);
        }

        public boolean hasWatchableHierarchies(Set<File> unwatchedHierarchies) {
            return !Sets.difference(watchableHierarchies, unwatchedHierarchies).isEmpty();
        }

        // Equals and hash code should only be used for set comparison, so WatchProbe are unique by probeFile
        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            WatchProbeImpl that = (WatchProbeImpl) o;
            return Objects.equals(probeFile, that.probeFile);
        }

        @Override
        public int hashCode() {
            return Objects.hash(probeFile);
        }
    }
}
