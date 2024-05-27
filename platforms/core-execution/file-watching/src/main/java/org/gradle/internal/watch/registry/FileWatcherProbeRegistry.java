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

package org.gradle.internal.watch.registry;

import com.google.common.collect.ImmutableSet;

import java.io.File;
import java.nio.file.Path;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

/**
 * A registry of watch probes. A probe serves as a way to prove that a hierarchy Gradle is interested in
 * indeed receives file system events from the operating system.
 * This is to avoid trusting locations where OSs silently not send any events, despite watchers being registered.
 *
 * When the hierarchy is first registered via {@link #registerProbe(File, File)}, we don't yet create the probe.
 * That only happens when the hierarchy is actually read or written by Gradle, in which case
 * watch probe is armed.
 *
 * When the probe is armed, a probe file is created (or re-created) for the hierarchy.
 * This should cause a file system event to be produced by the operating system.
 * We listen to those events specifically in {@link FileWatcherRegistry}.
 * Once the event arrives, {@link #triggerWatchProbe(Path)} is called with the path,
 * and the probe becomes triggered (or proven).
 *
 * The {@link #unprovenHierarchies()} stream returns any hierarchies that were armed, but never received
 * a file system event.
 * These locations cannot be trusted to receive file system events in a timely manner.
 *
 * Note: a probe needs to be armed only once, if it receives an event, we will trust that location until
 * the registry is closed.
 */
public interface FileWatcherProbeRegistry {
    void registerProbe(File hierarchy, File probeDirectory);

    void updateProbedHierarchies(ImmutableSet<File> probedHierarchies, BiConsumer<File, Boolean> probeDisarmed, BiConsumer<File, Boolean> beforeProbeArmed);

    void triggerWatchProbe(Path path);

    Stream<File> unprovenHierarchies();
}
