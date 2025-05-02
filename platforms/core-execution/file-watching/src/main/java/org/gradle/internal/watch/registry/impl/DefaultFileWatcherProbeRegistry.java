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
import java.util.function.Function;
import java.util.stream.Stream;

public class DefaultFileWatcherProbeRegistry implements FileWatcherProbeRegistry {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultFileWatcherProbeRegistry.class);

    private final Map<File, WatchProbe> watchProbesByHierarchy = new ConcurrentHashMap<>();
    private final Map<String, WatchProbe> watchProbesByPath = new ConcurrentHashMap<>();

    private final Function<File, File> probeLocationResolver;

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
            probe.trigger();
        }
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
        private final File probeFile;
        private State state = State.UNARMED;

        public WatchProbe(File watchableHierarchy, File probeFile) {
            this.watchableHierarchy = watchableHierarchy;
            this.probeFile = probeFile;
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

        public synchronized void trigger() {
            if (state != State.TRIGGERED) {
                LOGGER.debug("Watch probe in state {} has been triggered for hierarchy: {}", state, watchableHierarchy);
                state = State.TRIGGERED;
            }
        }

        public synchronized boolean leftArmed() {
            return state == State.ARMED;
        }

        public File getProbeFile() {
            return probeFile;
        }

        public File getWatchableHierarchy() {
            return watchableHierarchy;
        }
    }
}
