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

package org.gradle.internal.snapshot;

import org.gradle.internal.vfs.impl.SnapshotCollectingDiffListener;

import javax.annotation.CheckReturnValue;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Predicate;

public class AtomicSnapshotHierarchyReference {
    private volatile SnapshotHierarchy root;
    private final Predicate<String> watchFilter;
    private final ReentrantLock updateLock = new ReentrantLock();
    private ErrorHandlingDiffPublisher errorHandlingDiffPublisher;

    public AtomicSnapshotHierarchyReference(SnapshotHierarchy root, Predicate<String> watchFilter) {
        this.root = root;
        this.watchFilter = watchFilter;
    }

    public SnapshotHierarchy get() {
        return root;
    }

    public void update(SnapshotHierarchy.UpdateFunction updateFunction) {
        updateLock.lock();
        try {
            // Store the current root in a local variable to make the call atomic
            ErrorHandlingDiffPublisher currentErrorHandlingDiffPublisher = errorHandlingDiffPublisher;
            SnapshotHierarchy currentRoot = root;
            if (currentErrorHandlingDiffPublisher == null) {
                root = updateFunction.update(currentRoot, SnapshotHierarchy.NodeDiffListener.NOOP);
            } else {
                SnapshotCollectingDiffListener diffListener = new SnapshotCollectingDiffListener(watchFilter);
                SnapshotHierarchy newRoot = updateFunction.update(currentRoot, diffListener);
                root = currentErrorHandlingDiffPublisher.publishSnapshotDiff(diffListener, newRoot);
            }
        } finally {
            updateLock.unlock();
        }
    }

    public interface ErrorHandler {
        @CheckReturnValue
        SnapshotHierarchy handleErrors(SnapshotHierarchy currentRoot, Runnable runnable);
    }

    public void setSnapshotDiffListener(SnapshotHierarchy.SnapshotDiffListener snapshotDiffListener, ErrorHandler errorHandler) {
        this.errorHandlingDiffPublisher = new ErrorHandlingDiffPublisher(snapshotDiffListener, errorHandler);
    }

    public void stopListening() {
        errorHandlingDiffPublisher = null;
    }

    private static class ErrorHandlingDiffPublisher {
        private final SnapshotHierarchy.SnapshotDiffListener diffListener;
        private final ErrorHandler errorHandler;

        public ErrorHandlingDiffPublisher(SnapshotHierarchy.SnapshotDiffListener diffListener, ErrorHandler errorHandler) {
            this.diffListener = diffListener;
            this.errorHandler = errorHandler;
        }

        public SnapshotHierarchy publishSnapshotDiff(SnapshotCollectingDiffListener collectedDiff, SnapshotHierarchy newRoot) {
            return errorHandler.handleErrors(newRoot, () -> collectedDiff.publishSnapshotDiff(diffListener));
        }
    }
}
