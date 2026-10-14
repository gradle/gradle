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

package org.gradle.internal.execution.trace;

import org.gradle.api.logging.LogLevel;
import org.gradle.initialization.RootBuildLifecycleListener;
import org.gradle.internal.build.BuildStateRegistry;
import org.gradle.internal.buildoption.InternalOption;
import org.gradle.internal.buildoption.InternalOptions;
import org.gradle.internal.classpath.Instrumented;
import org.gradle.internal.initialization.layout.BuildTreeLocations;
import org.gradle.internal.logging.ConsoleRenderer;
import org.gradle.internal.logging.text.StyledTextOutput;
import org.gradle.internal.logging.text.StyledTextOutputFactory;
import org.gradle.internal.operations.BuildOperationListenerManager;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;
import org.gradle.internal.vfs.FileSystemAccess;
import org.jspecify.annotations.Nullable;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.List;

/**
 * Writes a "work trace" describing the scheduled task graph and every executed task as JSON Lines files.
 * <p>
 * Enabled with {@code -Dorg.gradle.internal.work.trace=«directory»}. An empty value writes to
 * {@code «root project build dir»/reports/work-trace/}. When the option is not set, this service does nothing.
 * <p>
 * The directory holds two files:
 * <ul>
 * <li>{@code plan.jsonl}: one {@code plan} record per build in the build tree, written once the build tree's task graph
 * has been calculated and recorded as a configuration input; untouched when no task graph is calculated (configuration cache hit),
 * so the previous build's file stays valid.</li>
 * <li>{@code work.jsonl}: the {@code projects} record followed by one {@code task} record per executed task. Truncated by every build.</li>
 * </ul>
 * Prototype: see {@link WorkTraceListener} for the record formats.
 */
@ServiceScope(Scope.BuildTree.class)
public class WorkTraceService implements RootBuildLifecycleListener {

    public static final String SYSPROP = "org.gradle.internal.work.trace";
    public static final String PLAN_FILE_NAME = "plan.jsonl";
    public static final String WORK_FILE_NAME = "work.jsonl";

    private static final InternalOption<@Nullable String> TRACE_OPTION = InternalOptions.ofStringOrNull(SYSPROP);

    private final BuildTreeLocations buildTreeLocations;
    private final BuildStateRegistry buildStateRegistry;
    private final BuildOperationListenerManager buildOperationListenerManager;
    private final StyledTextOutputFactory textOutputFactory;
    private final FileSystemAccess fileSystemAccess;

    private final @Nullable String configuredPath;
    private @Nullable WorkTraceListener listener;
    private @Nullable File traceDirectory;
    private @Nullable BufferedWriter workWriter;

    public WorkTraceService(
        InternalOptions internalOptions,
        BuildTreeLocations buildTreeLocations,
        BuildStateRegistry buildStateRegistry,
        BuildOperationListenerManager buildOperationListenerManager,
        StyledTextOutputFactory textOutputFactory,
        FileSystemAccess fileSystemAccess
    ) {
        this.buildTreeLocations = buildTreeLocations;
        this.buildStateRegistry = buildStateRegistry;
        this.buildOperationListenerManager = buildOperationListenerManager;
        this.textOutputFactory = textOutputFactory;
        this.fileSystemAccess = fileSystemAccess;
        this.configuredPath = internalOptions.getValueOrNull(TRACE_OPTION);
    }

    /**
     * Whether a work trace is written for this build.
     */
    public boolean isEnabled() {
        return configuredPath != null;
    }

    @Override
    public void afterStart() {
        // Always listening: the option itself is recorded as a configuration input even when the trace is off
        WorkTraceListener newListener = new WorkTraceListener(isEnabled(), this::writePlan, this::writeWorkLine, buildStateRegistry);
        listener = newListener;
        buildOperationListenerManager.addListener(newListener);
    }

    @Override
    public void beforeComplete(@Nullable Throwable failure) {
        WorkTraceListener currentListener = listener;
        if (currentListener == null) {
            return;
        }
        buildOperationListenerManager.removeListener(currentListener);
        listener = null;
        if (!isEnabled()) {
            return;
        }
        // Plan records not flushed yet (the work graph calculation did not complete normally)
        currentListener.flushPlan();
        // A build that ran no task (e.g. --dry-run) still gets the projects record
        currentListener.ensureProjectsRecordWritten();
        File directory = closeWorkFile();
        renderTraceUrl(directory);
    }

    private synchronized File closeWorkFile() {
        // work.jsonl is rewritten by every build that has the option set, even when no task ran
        ensureWorkWriterOpen();
        try {
            if (workWriter != null) {
                workWriter.close();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            workWriter = null;
        }
        return traceDirectory();
    }

    /**
     * Writes {@code plan.jsonl} in one go, replacing any previous content, and records it as a configuration input:
     * the file then belongs to the configuration cache entry that is being stored, and deleting or editing it
     * invalidates that entry. Only called when a task graph was calculated in this build.
     */
    private synchronized void writePlan(List<String> lines) {
        File planFile = new File(traceDirectory(), PLAN_FILE_NAME);
        try {
            // Invalidate the virtual file system entry first, so that the fingerprint hashes the new content
            fileSystemAccess.write(Collections.singletonList(planFile.getAbsolutePath()), () -> {
                try (BufferedWriter writer = open(PLAN_FILE_NAME)) {
                    for (String line : lines) {
                        write(writer, line);
                    }
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        Instrumented.fileObserved(planFile, WorkTraceService.class.getName());
    }

    private synchronized void writeWorkLine(String line) {
        write(ensureWorkWriterOpen(), line);
    }

    private BufferedWriter ensureWorkWriterOpen() {
        BufferedWriter writer = workWriter;
        if (writer == null) {
            writer = open(WORK_FILE_NAME);
            workWriter = writer;
        }
        return writer;
    }

    private static void write(BufferedWriter writer, String line) {
        try {
            writer.write(line);
            writer.write('\n');
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private BufferedWriter open(String fileName) {
        File directory = traceDirectory();
        try {
            Files.createDirectories(directory.toPath());
            return Files.newBufferedWriter(new File(directory, fileName).toPath(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Resolves the trace directory lazily: the default location is the root project's build directory,
     * which is only known once the root project has been configured.
     */
    private File traceDirectory() {
        File directory = traceDirectory;
        if (directory == null) {
            directory = resolveTraceDirectory();
            traceDirectory = directory;
        }
        return directory;
    }

    private File resolveTraceDirectory() {
        String path = configuredPath;
        if (path != null && !path.isEmpty()) {
            File directory = new File(path);
            return directory.isAbsolute() ? directory : new File(buildTreeLocations.getBuildTreeRootDirectory(), path);
        }
        return new File(rootProjectBuildDir(), "reports/work-trace");
    }

    private File rootProjectBuildDir() {
        try {
            return buildStateRegistry
                .getRootBuild()
                .getProjects()
                .getRootProject()
                .fromMutableState(project -> project.getLayout().getBuildDirectory().getAsFile().get());
        } catch (RuntimeException e) {
            // The root project may not have been configured (e.g. the configuration cache was loaded); use the conventional location
            return new File(buildTreeLocations.getBuildTreeRootDirectory(), "build");
        }
    }

    private void renderTraceUrl(File directory) {
        StyledTextOutput textOutput = textOutputFactory.create(WorkTraceService.class, LogLevel.LIFECYCLE);
        textOutput.println();
        textOutput.formatln("See the work trace at: %s", new ConsoleRenderer().asClickableFileUrl(directory));
    }
}
