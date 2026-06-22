/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.internal.tracing;

import jdk.jfr.Configuration;
import jdk.jfr.FlightRecorder;
import jdk.jfr.Recording;
import org.gradle.api.internal.StartParameterInternal;
import org.gradle.initialization.layout.BuildLayoutFactory;
import org.gradle.internal.buildoption.InternalOption;
import org.gradle.internal.buildoption.InternalOptions;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;
import org.jspecify.annotations.Nullable;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.ParseException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Starts and writes a Gradle-managed JFR recording for the duration of a build session.
 *
 * <p>This only manages the recording; it does not emit any Gradle events. Build-operation events are
 * emitted separately by {@code BuildOperationJfrEmitter} when {@code org.gradle.internal.operations.jfr}
 * is set, and are captured by this recording (as by any active recording) when both are enabled.
 *
 * <p>Two system properties control it:
 * <ul>
 *     <li>{@link #MANAGED_SYSPROP} ({@code org.gradle.internal.tracing.jfr.managed}) — when {@code true},
 *     Gradle starts its own JFR {@link Recording} as the build session begins and writes it out when the
 *     session ends. The recording uses the JDK's {@code default} profile, so it carries the usual JVM
 *     events without the user having to pass {@code -XX:StartFlightRecording} to the daemon JVM.</li>
 *     <li>{@link #DIR_SYSPROP} ({@code org.gradle.internal.tracing.jfr.dir}) — overrides where the
 *     {@code .jfr} file is written. By default it goes to the invoked build's root project directory;
 *     a relative value resolves against that root, an absolute value replaces it.</li>
 * </ul>
 */
@ServiceScope(Scope.BuildSession.class)
public class JfrRecordingManager implements Closeable {

    public static final String SYSPROP = "org.gradle.internal.tracing.jfr";

    public static final String MANAGED_SYSPROP = SYSPROP + ".managed";
    static final InternalOption<Boolean> ENABLED_OPTION = InternalOptions.ofBoolean(MANAGED_SYSPROP, false);

    public static final String DIR_SYSPROP = SYSPROP + ".dir";
    static final InternalOption<@Nullable String> DIR_OPTION = InternalOptions.ofStringOrNull(DIR_SYSPROP);

    private static final DateTimeFormatter FILE_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");

    private final @Nullable Recording recording;
    private final @Nullable Path destination;

    public JfrRecordingManager(InternalOptions options, StartParameterInternal startParameter, BuildLayoutFactory buildLayoutFactory) {
        if (!options.getBoolean(ENABLED_OPTION) || !FlightRecorder.isAvailable()) {
            this.recording = null;
            this.destination = null;
            return;
        }

        Recording started = null;
        Path target;
        try {
            File rootProjectDir = buildLayoutFactory.getLayoutFor(startParameter.toBuildLayoutConfiguration()).getRootDirectory();
            File dir = resolveOutputDir(options, rootProjectDir);
            Files.createDirectories(dir.toPath());
            target = dir.toPath().resolve(LocalDateTime.now(ZoneId.systemDefault()).format(FILE_TIMESTAMP) + ".jfr");
            // Seed from the JDK's "default" profile so the recording carries the usual JVM events
            // (GC, threads, etc.). Build-operation events, when emitted, are captured automatically.
            started = new Recording(Configuration.getConfiguration("default"));
            started.setName("Gradle Build Operations");
            // Enable streaming to disk
            // This is important, because otherwise we will keep the recording in memory
            started.setToDisk(true);
            started.setDestination(target);
            started.start();
        } catch (IOException | ParseException | RuntimeException e) {
            // Never let a diagnostic recording break the build.
            System.err.println("Could not start Gradle JFR recording: " + e.getMessage());
            if (started != null) {
                started.close();
            }
            started = null;
            target = null;
        }
        this.recording = started;
        this.destination = target;
    }

    private static File resolveOutputDir(InternalOptions options, File rootProjectDir) {
        String dir = options.getValueOrNull(DIR_OPTION);
        if (dir == null || dir.isEmpty()) {
            return rootProjectDir;
        }
        // An absolute .dir overrides the root project dir; a relative one resolves against it.
        return rootProjectDir.toPath().resolve(dir).toFile();
    }

    @Override
    public void close() {
        if (recording == null) {
            return;
        }
        // stop() flushes to the destination set at start; close() releases the repository chunks.
        recording.stop();
        recording.close();
        System.out.println("JFR recording written to " + destination);
    }
}
