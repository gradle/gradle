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

import org.gradle.internal.SystemProperties;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

public class FailurePrinter {

    public static String printToString(Failure failure) {
        StringBuilder output = new StringBuilder();
        print(output, failure, StackFramePredicate.TRUE, null);
        return output.toString();
    }

    public static void print(Appendable output, Failure failure, StackFramePredicate predicate, @Nullable FailurePrinterListener listener) {
        new Job(output, predicate, listener).print(failure);
    }

    private static final class Job {

        private final StackFramePredicate predicate;
        @Nullable
        private final FailurePrinterListener listener;

        private final Appendable builder;
        private final String lineSeparator = SystemProperties.getInstance().getLineSeparator();
        private final Set<Failure> seen = Collections.newSetFromMap(new IdentityHashMap<Failure, Boolean>());

        private Job(
            Appendable builder,
            StackFramePredicate predicate,
            @Nullable FailurePrinterListener listener
        ) {
            this.predicate = predicate;
            this.listener = listener;
            this.builder = builder;
        }

        public void print(Failure failure) {
            try {
                printRecursively("", "", null, failure);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        private void printRecursively(String caption, String prefix, @Nullable Failure parent, Failure failure) throws IOException {
            if (!seen.add(failure)) {
                builder.append(prefix)
                    .append(caption)
                    .append("[CIRCULAR REFERENCE: ")
                    .append(failure.getHeader())
                    .append("]")
                    .append(lineSeparator);
                return;
            }

            builder.append(prefix)
                .append(caption)
                .append(failure.getHeader())
                .append(lineSeparator);

            if (listener == null) {
                appendFrames(prefix, parent, failure);
            } else {
                listener.beforeFrames();
                appendFrames(prefix, parent, failure);
                listener.afterFrames();
            }

            appendSuppressed(prefix, failure);
            appendCauses(prefix, failure);
        }

        private void appendSuppressed(String prefix, Failure failure) throws IOException {
            for (Failure suppressed : failure.getSuppressed()) {
                printRecursively("Suppressed: ", prefix + "\t", failure, suppressed);
            }
        }

        private void appendCauses(String prefix, Failure failure) throws IOException {
            List<Failure> causes = failure.getCauses();
            if (causes.size() == 1) {
                printRecursively("Caused by: ", prefix, failure, causes.get(0));
            } else {
                for (int i = 0; i < causes.size(); i++) {
                    printRecursively(String.format("Cause %s: ", i + 1), prefix, failure, causes.get(i));
                }
            }
        }

        private void appendFrames(String prefix, @Nullable Failure parent, Failure failure) throws IOException {
            List<StackTraceElement> stackTrace = failure.getStackTrace();

            int commonTailSize = parent == null ? 0 : countCommonTailFrames(stackTrace, parent.getStackTrace());
            int end = stackTrace.size() - commonTailSize;

            for (int i = 0; i < end; i++) {
                StackTraceElement stackTraceElement = stackTrace.get(i);
                StackTraceRelevance rel = failure.getStackTraceRelevance(i);
                appendFrame(prefix, stackTraceElement, rel);
            }

            if (commonTailSize > 0) {
                builder.append(prefix)
                    .append("\t... ")
                    .append(String.valueOf(commonTailSize))
                    .append(" more")
                    .append(lineSeparator);
            }
        }

        private void appendFrame(String prefix, StackTraceElement frame, StackTraceRelevance relevance) throws IOException {
            if (!predicate.test(frame, relevance)) {
                return;
            }

            if (listener != null) {
                listener.beforeFrame(frame, relevance);
            }

            builder.append(prefix)
                .append("\tat ")
                .append(frame.toString())
                .append(lineSeparator);
        }

        private static int countCommonTailFrames(List<StackTraceElement> frames1, List<StackTraceElement> frames2) {
            int j1 = frames1.size() - 1;
            int j2 = frames2.size() - 1;
            while (j1 >= 0 && j2 >= 0 && frames1.get(j1).equals(frames2.get(j2))) {
                j1--;
                j2--;
            }
            return frames1.size() - (j1 + 1);
        }
    }
}
