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
import java.util.List;

public class DefaultFailurePrinter {

    public String print(Failure failure) {
        return print(failure, StackFramePredicate.TRUE);
    }

    public String print(Failure failure, StackFramePredicate predicate) {
        StringBuilder builder = new StringBuilder();
        String lineSeparator = SystemProperties.getInstance().getLineSeparator();
        printRecursively(builder, "", "", null, failure, predicate, lineSeparator);
        return builder.toString();
    }

    private static void printRecursively(
        StringBuilder builder,
        String caption,
        String prefix,
        @Nullable Failure parent,
        Failure failure,
        StackFramePredicate predicate,
        String lineSeparator
    ) {

        builder.append(prefix)
            .append(caption)
            .append(failure.getHeader())
            .append(lineSeparator);

        appendStackTrace(builder, prefix, parent, failure, predicate, lineSeparator);
        appendSuppressed(builder, prefix, failure, predicate, lineSeparator);
        appendCauses(builder, prefix, failure, predicate, lineSeparator);
    }

    private static void appendSuppressed(StringBuilder builder, String prefix, Failure failure, StackFramePredicate predicate, String lineSeparator) {
        for (Failure suppressed : failure.getSuppressed()) {
            printRecursively(builder, "Suppressed: ", prefix + "\t", failure, suppressed, predicate, lineSeparator);
        }
    }

    private static void appendCauses(StringBuilder builder, String prefix, Failure failure, StackFramePredicate predicate, String lineSeparator) {
        List<Failure> causes = failure.getCauses();
        if (causes.size() == 1) {
            printRecursively(builder, "Caused by: ", prefix, failure, causes.get(0), predicate, lineSeparator);
        } else {
            for (int i = 0; i < causes.size(); i++) {
                printRecursively(builder, String.format("Cause %s: ", i + 1), prefix, failure, causes.get(i), predicate, lineSeparator);
            }
        }
    }

    private static void appendStackTrace(StringBuilder builder, String prefix, @Nullable Failure parent, Failure failure, StackFramePredicate predicate, String lineSeparator) {
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
