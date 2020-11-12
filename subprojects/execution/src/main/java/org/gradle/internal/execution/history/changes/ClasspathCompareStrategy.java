/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.internal.execution.history.changes;

import org.gradle.internal.fingerprint.FileCollectionFingerprint;
import org.gradle.internal.fingerprint.FileSystemLocationFingerprint;

import javax.annotation.Nullable;
import java.util.Iterator;
import java.util.Map;

/**
 * Compares two {@link FileCollectionFingerprint}s representing classpaths.
 *
 * That means that the comparison happens in-order with relative path sensitivity.
 */
public class ClasspathCompareStrategy extends AbstractFingerprintCompareStrategy {
    public static final FingerprintCompareStrategy INSTANCE = new ClasspathCompareStrategy();

    private ClasspathCompareStrategy() {
        super(ClasspathCompareStrategy::visitChangesSince);
    }

    private static boolean visitChangesSince(
        Map<String, FileSystemLocationFingerprint> previousFingerprints,
        Map<String, FileSystemLocationFingerprint> currentFingerprints,
        String propertyTitle,
        ChangeVisitor visitor
    ) {
        TrackingVisitor trackingVisitor = new TrackingVisitor(visitor);
        ChangeState changeState = new ChangeState(propertyTitle, trackingVisitor, currentFingerprints, previousFingerprints);

        while (trackingVisitor.isConsumeMore() && changeState.hasMoreToProcess()) {
            changeState.processChange();
        }
        return trackingVisitor.isConsumeMore();
    }

    private static class TrackingVisitor implements ChangeVisitor {
        private final ChangeVisitor visitor;
        private boolean consumeMore = true;

        private TrackingVisitor(ChangeVisitor visitor) {
            this.visitor = visitor;
        }

        @Override
        public boolean visitChange(Change change) {
            if (consumeMore) {
                consumeMore = visitor.visitChange(change);
            }
            return consumeMore;
        }

        public boolean isConsumeMore() {
            return consumeMore;
        }
    }

    private static class ChangeState {
        private Map.Entry<String, FileSystemLocationFingerprint> current;
        private Map.Entry<String, FileSystemLocationFingerprint> previous;
        private final ChangeVisitor changeConsumer;
        private final Iterator<Map.Entry<String, FileSystemLocationFingerprint>> currentEntries;
        private final Map<String, FileSystemLocationFingerprint> currentSnapshots;
        private final Iterator<Map.Entry<String, FileSystemLocationFingerprint>> previousEntries;
        private final Map<String, FileSystemLocationFingerprint> previousSnapshots;
        private final String propertyTitle;

        private ChangeState(String propertyTitle, ChangeVisitor changeConsumer, Map<String, FileSystemLocationFingerprint> currentSnapshots, Map<String, FileSystemLocationFingerprint> previousSnapshots) {
            this.propertyTitle = propertyTitle;
            this.changeConsumer = changeConsumer;
            this.currentEntries = currentSnapshots.entrySet().iterator();
            this.currentSnapshots = currentSnapshots;
            this.previousEntries = previousSnapshots.entrySet().iterator();
            this.previousSnapshots = previousSnapshots;
            this.current = nextEntry(currentEntries);
            this.previous = nextEntry(previousEntries);
        }

        void processChange() {
            if (current == null) {
                if (previous != null) {
                    removed();
                }
            } else if (previous == null) {
                added();
            } else {
                FileSystemLocationFingerprint currentFingerprint = current.getValue();
                FileSystemLocationFingerprint previousFingerprint = previous.getValue();
                String currentNormalizedPath = currentFingerprint.getNormalizedPath();
                String previousNormalizedPath = previousFingerprint.getNormalizedPath();
                if (!currentNormalizedPath.equals(previousNormalizedPath)) {
                    removed();
                    added();
                } else if (currentFingerprint.getNormalizedContentHash().equals(previousFingerprint.getNormalizedContentHash())) {
                    current = nextEntry(currentEntries);
                    previous = nextEntry(previousEntries);
                } else if (currentNormalizedPath.isEmpty()) {
                    String currentAbsolutePath = current.getKey();
                    String previousAbsolutePath = previous.getKey();
                    if (!currentAbsolutePath.equals(previousAbsolutePath)) {
                        if (!currentSnapshots.containsKey(previousAbsolutePath)) {
                            removed();
                        } else if (!previousSnapshots.containsKey(currentAbsolutePath)) {
                            added();
                        } else {
                            removed();
                            added();
                        }
                    } else {
                        modified();
                    }
                } else {
                    modified();
                }
            }
        }

        void added() {
            DefaultFileChange added = DefaultFileChange.added(current.getKey(), propertyTitle, current.getValue().getType(), current.getValue().getNormalizedPath());
            changeConsumer.visitChange(added);
            current = nextEntry(currentEntries);
        }

        void removed() {
            DefaultFileChange removed = DefaultFileChange.removed(previous.getKey(), propertyTitle, previous.getValue().getType(), previous.getValue().getNormalizedPath());
            changeConsumer.visitChange(removed);
            previous = nextEntry(previousEntries);
        }

        void modified() {
            DefaultFileChange modified = DefaultFileChange.modified(current.getKey(), propertyTitle, previous.getValue().getType(), current.getValue().getType(), current.getValue().getNormalizedPath());
            changeConsumer.visitChange(modified);
            previous = nextEntry(previousEntries);
            current = nextEntry(currentEntries);
        }

        @Nullable
        private <T> T nextEntry(Iterator<T> iterator) {
            return iterator.hasNext() ? iterator.next() : null;
        }

        public boolean hasMoreToProcess() {
            return current != null || previous != null;
        }
    }
}
