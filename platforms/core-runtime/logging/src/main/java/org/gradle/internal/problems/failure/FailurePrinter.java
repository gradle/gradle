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
import org.gradle.internal.UncheckedException;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.List;

/**
 * Utility to print {@link Failure}s in the format matching that of {@link Throwable#printStackTrace()}.
 * <p>
 * Failures with multiple causes are printed similarly to {@link org.gradle.internal.exceptions.MultiCauseException}.
 * <p>
 * The printer additionally allows reacting to each frame to be printed via a {@link FailurePrinterListener}.
 */
public class FailurePrinter {

    public static String printToString(Failure failure) {
        StringBuilder output = new StringBuilder();
        print(output, failure, FailurePrinterListener.NO_OP);
        return output.toString();
    }

    public static void print(Appendable output, Failure failure, FailurePrinterListener listener) {
        new Job(output, listener).print(failure);
    }

    private static final class Job {

        private final FailurePrinterListener listener;

        private final Appendable builder;
        private final String lineSeparator = SystemProperties.getInstance().getLineSeparator();

        private Job(Appendable builder, FailurePrinterListener listener) {
            this.listener = listener;
            this.builder = builder;
        }

        public void print(Failure failure) {
            try {
                printRecursively("", "", null, failure);
            } catch (IOException e) {
                throw UncheckedException.throwAsUncheckedException(e);
            }
        }

        private void printRecursively(String caption, String prefix, @Nullable Failure parent, Failure failure) throws IOException {
            builder.append(prefix)
                .append(caption)
                .append(failure.getHeader())
                .append(lineSeparator);

            listener.beforeFrames();
            appendFrames(prefix, parent, failure);
            listener.afterFrames();

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
            listener.beforeFrame(frame, relevance);

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
