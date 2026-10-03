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

import com.google.common.primitives.Longs;
import org.gradle.internal.watch.registry.FileWatcherProbeRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.stream.Stream;

public class DefaultFileWatcherProbeRegistry implements FileWatcherProbeRegistry {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultFileWatcherProbeRegistry.class);

    private final Map<File, WatchProbe> watchProbesByHierarchy = new ConcurrentHashMap<>();
    private final Map<String, WatchProbe> watchProbesByPath = new ConcurrentHashMap<>();

    private final Function<File, File> probeLocationResolver;
    private final AtomicLong nextProbeGeneration = new AtomicLong();

    public DefaultFileWatcherProbeRegistry(Function<File, File> probeLocationResolver) {
        this.probeLocationResolver = probeLocationResolver;
    }

    @Override
    public void registerProbe(File watchableHierarchy) {
        if (watchProbesByHierarchy.containsKey(watchableHierarchy)) {
            // Already registered
            return;
        }
        LOGGER.debug("Registering probe for {}", watchableHierarchy);
        File probeFile = probeLocationResolver.apply(watchableHierarchy);
        WatchProbe watchProbe = new WatchProbe(watchableHierarchy, probeFile);
        watchProbesByHierarchy.put(watchableHierarchy, watchProbe);
        watchProbesByPath.put(probeFile.getAbsolutePath(), watchProbe);
    }

    @Override
    public Stream<File> unprovenHierarchies() {
        return watchProbesByHierarchy.values().stream()
            .filter(WatchProbe::leftArmed)
            .map(WatchProbe::getWatchableHierarchy);
    }

    @Override
    public boolean isProbeFile(String path) {
        File file = new File(path);
        File directory = file.getParentFile();
        if (directory == null) {
            return false;
        }
        return watchProbesByHierarchy.values().stream()
            .anyMatch(probe -> probe.ownsProbeFile(directory, file.getName()));
    }

    @Override
    public boolean isProbeDirectory(String path) {
        File directory = new File(path);
        return watchProbesByHierarchy.values().stream().anyMatch(probe -> probe.ownsProbeDirectory(directory));
    }

    @Override
    public boolean hasUnprovenHierarchies() {
        return watchProbesByHierarchy.values().stream().anyMatch(WatchProbe::leftArmed);
    }

    @Override
    public void rearmWatchProbe(File watchableHierarchy) {
        WatchProbe probe = watchProbesByHierarchy.get(watchableHierarchy);
        if (probe == null) {
            LOGGER.debug("Did not find watchable hierarchy to re-arm probe for: {}", watchableHierarchy);
            return;
        }
        File previousProbeFile = probe.getProbeFile();
        File probeFile = new File(previousProbeFile.getParentFile(),
            probe.nameForGeneration(nextProbeGeneration.incrementAndGet()));
        // Both mappings move before the file is written: no event for the new name is dropped because
        // the map was not ready, and none for the old name still resolves to this probe.
        watchProbesByPath.remove(previousProbeFile.getAbsolutePath());
        watchProbesByPath.put(probeFile.getAbsolutePath(), probe);
        try {
            probe.rearm(probeFile);
        } catch (IOException e) {
            LOGGER.debug("Could not re-arm watch probe for hierarchy {}", watchableHierarchy, e);
        }
    }

    @Override
    public void armWatchProbe(File watchableHierarchy) {
        WatchProbe probe = watchProbesByHierarchy.get(watchableHierarchy);
        if (probe != null) {
            try {
                probe.arm();
            } catch (IOException e) {
                LOGGER.debug("Could not arm watch probe for hierarchy {}", watchableHierarchy, e);
            }
        } else {
            LOGGER.debug("Did not find watchable hierarchy to arm probe for: {}", watchableHierarchy);
        }
    }

    @Override
    public void disarmWatchProbe(File watchableHierarchy) {
        WatchProbe probe = watchProbesByHierarchy.get(watchableHierarchy);
        if (probe != null) {
            probe.disarm();
        } else {
            LOGGER.debug("Did not find watchable hierarchy to disarm probe for: {}", watchableHierarchy);
        }
    }

    /**
     * Triggers a watch probe at the given location if one exists.
     */
    @Override
    public void triggerWatchProbe(String path) {
        WatchProbe probe = watchProbesByPath.get(path);
        if (probe != null) {
            LOGGER.debug("Triggering watch probe for {}", probe.getWatchableHierarchy());
            probe.trigger(path);
        }
    }

    @Override
    public void removeProbeFiles() {
        watchProbesByHierarchy.values().forEach(WatchProbe::deleteProbeFile);
    }

    @Override
    public File getProbeDirectory(File hierarchy) {
        WatchProbe watchProbe = watchProbesByHierarchy.get(hierarchy);
        if (watchProbe == null) {
            throw new IllegalStateException("Cannot find probe for hierarchy: " + hierarchy);
        }
        return watchProbe.getProbeFile().getParentFile();
    }

    private static class WatchProbe {
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

        private final File watchableHierarchy;
        /**
         * Name every probe file of this hierarchy starts with, before the generation suffix. The probe
         * directory holds unrelated files, so {@link #rearm(File)} sweeps by this prefix rather than
         * emptying the directory.
         */
        private final String baseName;
        private final String extension;
        private volatile File probeFile;
        private volatile State state = State.UNARMED;

        public WatchProbe(File watchableHierarchy, File probeFile) {
            this.watchableHierarchy = watchableHierarchy;
            this.probeFile = probeFile;
            String name = probeFile.getName();
            int dot = name.lastIndexOf('.');
            this.baseName = dot < 0 ? name : name.substring(0, dot);
            this.extension = dot < 0 ? "" : name.substring(dot);
        }

        /**
         * Names the given generation of this probe. The generation is a suffix on the original name, so
         * a base name that itself contains a hyphen keeps it.
         */
        public String nameForGeneration(long generation) {
            return baseName + "-" + generation + extension;
        }

        /**
         * Returns whether the name is one this probe writes: the name it was registered with, or any
         * generation of it. The sweep and {@link DefaultFileWatcherProbeRegistry#isProbeFile} share
         * this so they cannot drift apart.
         */
        boolean isOwnProbeFileName(String name) {
            if (!name.startsWith(baseName) || !name.endsWith(extension)) {
                return false;
            }
            String generation = name.substring(baseName.length(), name.length() - extension.length());
            if (generation.isEmpty()) {
                return true;
            }
            if (generation.length() < 2 || generation.charAt(0) != '-') {
                return false;
            }
            for (int i = 1; i < generation.length(); i++) {
                if (!Character.isDigit(generation.charAt(i))) {
                    return false;
                }
            }
            return true;
        }

        boolean ownsProbeFile(File directory, String name) {
            return directory.equals(probeFile.getParentFile()) && isOwnProbeFileName(name);
        }

        boolean ownsProbeDirectory(File directory) {
            return directory.equals(probeFile.getParentFile());
        }


        public synchronized void arm() throws IOException {
            switch (state) {
                case UNARMED:
                    state = State.ARMED;
                    //noinspection ResultOfMethodCallIgnored
                    probeFile.getParentFile().mkdirs();
                    try (FileOutputStream out = new FileOutputStream(probeFile)) {
                        out.write(Longs.toByteArray(System.currentTimeMillis()));
                    }
                    LOGGER.debug("Watch probe has been armed for hierarchy: {}", watchableHierarchy);
                    break;
                case ARMED:
                    LOGGER.debug("Watch probe for hierarchy is already armed: {}", watchableHierarchy);
                    break;
                case TRIGGERED:
                    LOGGER.debug("Watch probe for hierarchy has already been triggered: {}", watchableHierarchy);
                    break;
                default:
                    throw new AssertionError();
            }
        }

        public synchronized void disarm() {
            switch (state) {
                case UNARMED:
                    LOGGER.debug("Watch probe has already been disarmed for hierarchy: {}", watchableHierarchy);
                    break;
                case ARMED:
                    state = State.UNARMED;
                    LOGGER.debug("Watch probe has been disarmed for hierarchy: {}", watchableHierarchy);
                    break;
                case TRIGGERED:
                    LOGGER.debug("Watch probe has already been triggered for hierarchy: {}", watchableHierarchy);
                    break;
            }
        }

        public synchronized void trigger(String eventPath) {
            if (!probeFile.getAbsolutePath().equals(eventPath)) {
                // An event for a generation this probe has already superseded.
                LOGGER.debug("Ignoring watch probe event for a superseded generation: {}", eventPath);
                return;
            }
            if (state != State.TRIGGERED) {
                LOGGER.debug("Watch probe in state {} has been triggered for hierarchy: {}", state, watchableHierarchy);
                state = State.TRIGGERED;
            }
        }

        public synchronized void rearm(File newProbeFile) throws IOException {
            File[] previousGenerations = probeFile.getParentFile()
                .listFiles((directory, name) -> isOwnProbeFileName(name));
            if (previousGenerations != null) {
                for (File file : previousGenerations) {
                    //noinspection ResultOfMethodCallIgnored
                    file.delete();
                }
            }
            probeFile = newProbeFile;
            state = State.UNARMED;
            arm();
        }

        public boolean leftArmed() {
            return state == State.ARMED;
        }

        public synchronized void deleteProbeFile() {
            if (!probeFile.delete() && probeFile.exists()) {
                LOGGER.debug("Could not delete probe file: {}", probeFile);
            }
            // The directory stays. Arming re-creates it at the start of every build, so removing it
            // here would make Gradle emit a create and a remove for it per build, in a location a
            // continuous build can be watching.
        }

        public File getProbeFile() {
            return probeFile;
        }

        public File getWatchableHierarchy() {
            return watchableHierarchy;
        }
    }
}
