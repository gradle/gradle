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

import java.util.List;

public class DefaultFailurePrinter {

    public String print(Failure failure) {
        return print(failure, StackFramePredicate.TRUE);
    }

    public String print(Failure failure, StackFramePredicate predicate) {
        StringBuilder builder = new StringBuilder();
        String lineSeparator = SystemProperties.getInstance().getLineSeparator();
        printImpl(builder, "", "", failure, predicate, lineSeparator);
        return builder.toString();
    }

    private static void printImpl(
        StringBuilder builder,
        String caption,
        String prefix,
        Failure failure,
        StackFramePredicate predicate,
        String lineSeparator
    ) {
        builder.append(prefix)
            .append(caption)
            .append(failure.getHeader())
            .append(lineSeparator);

        int i = 0;
        for (StackTraceElement stackTraceElement : failure.getStackTrace()) {
            StackTraceRelevance rel = failure.getStackTraceRelevance(i);
            if (predicate.test(stackTraceElement, rel)) {
                builder.append(prefix)
                    .append("\tat ")
                    .append(stackTraceElement)
                    .append(lineSeparator);
            }
            i++;
        }

        List<Failure> causes = failure.getCauses();
        if (causes.size() == 1) {
            printImpl(builder, "Caused by: ", prefix, causes.get(0), predicate, lineSeparator);
        } else {
            for (int j = 0; j < causes.size(); j++) {
                printImpl(builder, String.format("Cause %s: ", j + 1), prefix, causes.get(j), predicate, lineSeparator);
            }
        }
    }

}
