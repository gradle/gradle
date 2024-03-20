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

package org.gradle.internal.failure;

import org.gradle.internal.SystemProperties;
import org.gradle.internal.problems.failure.Failure;
import org.gradle.internal.problems.failure.StackTraceRelevance;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

public class DefaultFailurePrinter {

    public String print(Failure failure) {
        return print(failure, StackFramePredicate.TRUE);
    }

    public String print(Failure failure, StackFramePredicate predicate) {
        return new Printing(predicate).print(failure);
    }

    private static final class Printing {

        private final StackFramePredicate predicate;

        private final StringBuilder builder = new StringBuilder();
        private final String lineSeparator = SystemProperties.getInstance().getLineSeparator();
        private final Set<Failure> seen = Collections.newSetFromMap(new IdentityHashMap<Failure, Boolean>());

        private Printing(StackFramePredicate predicate) {
            this.predicate = predicate;
        }

        public String print(Failure failure) {
            printRecursively("", "", null, failure);
            return builder.toString();
        }

        private void printRecursively(String caption, String prefix, @Nullable Failure parent, Failure failure) {
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

            appendStackTrace(prefix, parent, failure);
            appendSuppressed(prefix, failure);
            appendCauses(prefix, failure);
        }

        private void appendSuppressed(String prefix, Failure failure) {
            for (Failure suppressed : failure.getSuppressed()) {
                printRecursively("Suppressed: ", prefix + "\t", failure, suppressed);
            }
        }

        private void appendCauses(String prefix, Failure failure) {
            List<Failure> causes = failure.getCauses();
            if (causes.size() == 1) {
                printRecursively("Caused by: ", prefix, failure, causes.get(0));
            } else {
                for (int i = 0; i < causes.size(); i++) {
                    printRecursively(String.format("Cause %s: ", i + 1), prefix, failure, causes.get(i));
                }
            }
        }

        private void appendStackTrace(String prefix, @Nullable Failure parent, Failure failure) {
            List<StackTraceElement> stackTrace = failure.getStackTrace();

            int commonTailSize = parent == null ? 0 : countCommonTailFrames(stackTrace, parent.getStackTrace());
            int end = stackTrace.size() - commonTailSize;

            for (int i = 0; i < end; i++) {
                StackTraceElement stackTraceElement = stackTrace.get(i);
                StackTraceRelevance rel = failure.getStackTraceRelevance(i);

                if (predicate.test(stackTraceElement, rel)) {
                    builder.append(prefix)
                        .append("\tat ")
                        .append(stackTraceElement)
                        .append(lineSeparator);
                }
            }

            if (commonTailSize > 0) {
                builder.append(prefix)
                    .append("\t... ")
                    .append(commonTailSize)
                    .append(" more")
                    .append(lineSeparator);
            }
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
