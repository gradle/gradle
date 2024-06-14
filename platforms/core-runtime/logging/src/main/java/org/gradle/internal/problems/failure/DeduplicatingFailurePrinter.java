/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.internal.problems.failure;

import javax.annotation.Nullable;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Formats {@link Failure} instances into stacktraces and keeps track of the unique traces.
 * <p>
 * The uniqueness of traces is determined by the N topmost stack frames.
 */
public class DeduplicatingFailurePrinter {

    private final int deduplicationFramesCount;

    private final Set<String> seenPrefixes = new CopyOnWriteArraySet<String>();

    public DeduplicatingFailurePrinter() {
        this(10);
    }

    public DeduplicatingFailurePrinter(int deduplicationFramesCount) {
        this.deduplicationFramesCount = deduplicationFramesCount;
    }

    @Nullable
    public String printToString(Failure failure) {
        return new Job().run(failure);
    }

    private class Job implements FailurePrinterListener {

        private final StringBuilder buffer = new StringBuilder();
        private boolean isUnique = true;
        private int framesBeforeDeduplication = deduplicationFramesCount;

        @Nullable
        public String run(Failure failure) {
            FailurePrinter.print(buffer, failure, this);
            return isUnique ? buffer.toString() : null;
        }

        @Override
        public VisitResult beforeFrames() {
            return VisitResult.CONTINUE;
        }

        @Override
        public VisitResult beforeFrame(StackTraceElement element, StackTraceRelevance relevance) {
            if (framesBeforeDeduplication < 0) {
                return VisitResult.TERMINATE;
            }

            if (framesBeforeDeduplication == 0) {
                String deduplicationKey = buffer.toString();
                boolean newFailure = seenPrefixes.add(deduplicationKey);
                if (!newFailure) {
                    isUnique = false;
                    return VisitResult.TERMINATE;
                }
            }

            framesBeforeDeduplication--;
            return VisitResult.CONTINUE;
        }

        @Override
        public VisitResult afterFrames() {
            return VisitResult.CONTINUE;
        }
    }
}
