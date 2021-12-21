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

package org.gradle.tooling.internal.provider;

import org.gradle.api.execution.internal.FileChangeListener;
import org.gradle.api.execution.internal.TaskInputsListener;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.file.FileCollectionInternal;
import org.gradle.api.internal.file.FileCollectionStructureVisitor;
import org.gradle.api.internal.file.FileTreeInternal;
import org.gradle.api.internal.file.collections.FileSystemMirroringFileTree;
import org.gradle.api.tasks.util.PatternSet;
import org.gradle.execution.plan.InputAccessHierarchy;
import org.gradle.initialization.BuildCancellationToken;
import org.gradle.initialization.ContinuousExecutionGate;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.filewatch.PendingChangesListener;
import org.gradle.internal.logging.text.StyledTextOutput;
import org.gradle.internal.watch.registry.FileWatcherRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.gradle.internal.filewatch.DefaultFileSystemChangeWaiterFactory.QUIET_PERIOD_SYSPROP;

public class FileSystemChangeListener implements FileChangeListener, TaskInputsListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(FileSystemChangeListener.class);

    private final InputAccessHierarchy inputAccessHierarchy;
    private final PendingChangesListener pendingChangesListener;
    private final BuildCancellationToken cancellationToken;
    private final ContinuousExecutionGate continuousExecutionGate;
    private final AtomicBoolean changeArrived = new AtomicBoolean(false);
    private final CountDownLatch changeOrCancellationArrived = new CountDownLatch(1);
    private final CountDownLatch cancellationArrived = new CountDownLatch(1);
    private final FileEventCollector fileEventCollector = new FileEventCollector();
    private final long quietPeriod;
    private volatile long lastChangeAt = monotonicClockMillis();

    public FileSystemChangeListener(
        InputAccessHierarchy inputAccessHierarchy,
        PendingChangesListener pendingChangesListener,
        BuildCancellationToken cancellationToken,
        ContinuousExecutionGate continuousExecutionGate
    ) {
        this.inputAccessHierarchy = inputAccessHierarchy;
        this.pendingChangesListener = pendingChangesListener;
        this.cancellationToken = cancellationToken;
        this.continuousExecutionGate = continuousExecutionGate;
        this.quietPeriod = Long.getLong(QUIET_PERIOD_SYSPROP, 250L);
    }

    public boolean hasAnyInputs() {
        return !inputAccessHierarchy.isEmpty();
    }

    void wait(Runnable notifier) {
        Runnable cancellationHandler = () -> {
            changeOrCancellationArrived.countDown();
            cancellationArrived.countDown();
        };
        if (cancellationToken.isCancellationRequested()) {
            return;
        }
        try {
            cancellationToken.addCallback(cancellationHandler);
            notifier.run();
            changeOrCancellationArrived.await();
            while (!cancellationToken.isCancellationRequested()) {
                long now = monotonicClockMillis();
                long remainingQuietPeriod = quietPeriod - (now - lastChangeAt);
                if (remainingQuietPeriod <= 0) {
                    break;
                }
                cancellationArrived.await(remainingQuietPeriod, TimeUnit.MILLISECONDS);
            }
            if (!cancellationToken.isCancellationRequested()) {
                continuousExecutionGate.waitForOpen();
            }
        } catch (InterruptedException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        } finally {
            cancellationToken.removeCallback(cancellationHandler);
        }
    }

    public void reportChanges(StyledTextOutput logger) {
        fileEventCollector.reportChanges(logger);
    }

    @Override
    public void handleChange(FileWatcherRegistry.Type type, Path path) {
        String absolutePath = path.toString();
        lastChangeAt = monotonicClockMillis();
        if (inputAccessHierarchy.isInput(absolutePath)) {
            // got a change, store it
            fileEventCollector.onChange(type, path);
            notifyChangeArrived();
        }
    }

    @Override
    public void stopWatchingAfterError() {
        fileEventCollector.errorWhenWatching();
        notifyChangeArrived();
    }

    private void notifyChangeArrived() {
        changeOrCancellationArrived.countDown();
        if (changeArrived.compareAndSet(false, true)) {
            pendingChangesListener.onPendingChanges();
        }
    }

    @Override
    public synchronized void onExecute(TaskInternal task, FileCollectionInternal fileSystemInputs) {
        Set<String> taskInputs = new LinkedHashSet<>();
        Set<FilteredTree> filteredFileTreeTaskInputs = new LinkedHashSet<>();
        fileSystemInputs.visitStructure(new FileCollectionStructureVisitor() {
            @Override
            public void visitCollection(FileCollectionInternal.Source source, Iterable<File> contents) {
                contents.forEach(location -> taskInputs.add(location.getAbsolutePath()));
            }

            @Override
            public void visitGenericFileTree(FileTreeInternal fileTree, FileSystemMirroringFileTree sourceTree) {
                fileTree.forEach(location -> taskInputs.add(location.getAbsolutePath()));
            }

            @Override
            public void visitFileTree(File root, PatternSet patterns, FileTreeInternal fileTree) {
                if (patterns.isEmpty()) {
                    taskInputs.add(root.getAbsolutePath());
                } else {
                    filteredFileTreeTaskInputs.add(new FilteredTree(root.getAbsolutePath(), patterns));
                }
            }

            @Override
            public void visitFileTreeBackedByFile(File file, FileTreeInternal fileTree, FileSystemMirroringFileTree sourceTree) {
                taskInputs.add(file.getAbsolutePath());
            }
        });
        inputAccessHierarchy.recordInputs(taskInputs);
        filteredFileTreeTaskInputs.forEach(fileTree -> inputAccessHierarchy.recordFilteredInput(fileTree.getRoot(), fileTree.getPatterns().getAsSpec()));
    }

    private static long monotonicClockMillis() {
        return System.nanoTime() / 1000000L;
    }

    private static class FileEventCollector {
        private static final int SHOW_INDIVIDUAL_CHANGES_LIMIT = 3;
        private final Map<Path, FileWatcherRegistry.Type> aggregatedEvents = new LinkedHashMap<>();
        private int moreChangesCount;
        private boolean errorWhenWatching;


        public void onChange(FileWatcherRegistry.Type type, Path path) {
            FileWatcherRegistry.Type existingEvent = aggregatedEvents.get(path);
            if (existingEvent == type ||
                (existingEvent == FileWatcherRegistry.Type.CREATED && type == FileWatcherRegistry.Type.MODIFIED)) {
                return;
            }

            if (existingEvent != null || aggregatedEvents.size() < SHOW_INDIVIDUAL_CHANGES_LIMIT) {
                aggregatedEvents.put(path, type);
            } else {
                moreChangesCount++;
            }
        }

        public void errorWhenWatching() {
            errorWhenWatching = true;
        }

        public void reportChanges(StyledTextOutput logger) {
            for (Map.Entry<Path, FileWatcherRegistry.Type> entry : aggregatedEvents.entrySet()) {
                FileWatcherRegistry.Type type = entry.getValue();
                Path path = entry.getKey();
                showIndividualChange(logger, path, type);
            }
            if (moreChangesCount > 0) {
                logOutput(logger, "and some more changes");
            }
            if (errorWhenWatching) {
                logOutput(logger, "Error when watching files - triggering a rebuild");
            }
        }

        private void showIndividualChange(StyledTextOutput logger, Path path, FileWatcherRegistry.Type changeType) {
            String changeDescription;
            switch (changeType) {
                case CREATED:
                    changeDescription = "new " + (Files.isDirectory(path) ? "directory" : "file");
                    break;
                case REMOVED:
                    changeDescription = "deleted";
                    break;
                case MODIFIED:
                default:
                    changeDescription = "modified";
            }
            logOutput(logger, "%s: %s", changeDescription, path.toString());
        }

        private void logOutput(StyledTextOutput logger, String message, Object... objects) {
            logger.formatln(message, objects);
        }
    }

    private static class FilteredTree {
        private final String root;
        private final PatternSet patterns;

        private FilteredTree(String root, PatternSet patterns) {
            this.root = root;
            this.patterns = patterns;
        }

        public String getRoot() {
            return root;
        }

        public PatternSet getPatterns() {
            return patterns;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            FilteredTree that = (FilteredTree) o;
            return root.equals(that.root) && patterns.equals(that.patterns);
        }

        @Override
        public int hashCode() {
            return Objects.hash(root, patterns);
        }

    }
}
