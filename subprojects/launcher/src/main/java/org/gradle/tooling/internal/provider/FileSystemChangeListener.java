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

import com.google.common.collect.ImmutableMap;
import org.gradle.api.execution.internal.FileChangeListener;
import org.gradle.api.execution.internal.TaskInputsListener;
import org.gradle.api.internal.TaskInternal;
import org.gradle.initialization.BuildCancellationToken;
import org.gradle.initialization.ContinuousExecutionGate;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.file.FileHierarchySet;
import org.gradle.internal.filewatch.FileWatcherEvent;
import org.gradle.internal.filewatch.FileWatcherEventListener;
import org.gradle.internal.filewatch.PendingChangesListener;
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint;
import org.gradle.internal.watch.registry.FileWatcherRegistry;
import org.gradle.internal.watch.registry.impl.Combiners;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Path;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class FileSystemChangeListener implements FileChangeListener, TaskInputsListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(FileSystemChangeListener.class);

    private final PendingChangesListener pendingChangesListener;
    private final BuildCancellationToken cancellationToken;
    private final ContinuousExecutionGate continuousExecutionGate;
    private final BlockingQueue<String> pendingChanges = new LinkedBlockingQueue<>(1);
    private volatile FileHierarchySet inputs = FileHierarchySet.empty();

    public FileSystemChangeListener(PendingChangesListener pendingChangesListener, BuildCancellationToken cancellationToken, ContinuousExecutionGate continuousExecutionGate) {
        this.pendingChangesListener = pendingChangesListener;
        this.cancellationToken = cancellationToken;
        this.continuousExecutionGate = continuousExecutionGate;
    }

    public boolean hasAnyInputs() {
        return inputs != FileHierarchySet.empty();
    }

    void wait(Runnable notifier, FileWatcherEventListener eventListener) {
        Runnable cancellationHandler = () -> pendingChanges.offer("Build cancelled");
        if (cancellationToken.isCancellationRequested()) {
            return;
        }
        try {
            cancellationToken.addCallback(cancellationHandler);
            notifier.run();
            String pendingChange = pendingChanges.take();
            LOGGER.info("Received pending change: {}", pendingChange);
            eventListener.onChange(FileWatcherEvent.modify(new File(pendingChange)));
            if (!cancellationToken.isCancellationRequested()) {
                continuousExecutionGate.waitForOpen();
            }
        } catch (InterruptedException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        } finally {
            cancellationToken.removeCallback(cancellationHandler);
        }
    }

    @Override
    public void handleChange(FileWatcherRegistry.Type type, Path path) {
        String absolutePath = path.toString();
        if (inputs.contains(absolutePath)) {
            // got a change, store it
            if (pendingChanges.offer(absolutePath)) {
                pendingChangesListener.onPendingChanges();
            }
        }
    }

    @Override
    public void stopWatchingAfterError() {
        if (pendingChanges.offer("Error watching files")) {
            pendingChangesListener.onPendingChanges();
        }
    }

    @Override
    public synchronized void onExecute(TaskInternal task, ImmutableMap<String, CurrentFileCollectionFingerprint> fingerprints) {
        this.inputs = fingerprints.values().stream()
            .flatMap(fingerprint -> fingerprint.getRootHashes().keySet().stream())
            .reduce(inputs, FileHierarchySet::plus, Combiners.nonCombining());
    }
}
