/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.internal.operations;

import org.gradle.internal.exceptions.DefaultMultiCauseException;

import javax.annotation.Nullable;
import java.util.Collection;

public class MultipleBuildOperationFailures extends DefaultMultiCauseException {
    private static final int MAX_CAUSES = 10;

    public MultipleBuildOperationFailures(Collection<? extends Throwable> causes, @Nullable String logLocation) {
        super(format(getFailureMessage(causes), causes, logLocation), causes);
    }

    private static String getFailureMessage(Collection<? extends Throwable> failures) {
        if (failures.size() == 1) {
            return "A build operation failed.";
        }
        return "Multiple build operations failed.";
    }

    private static String format(String message, Iterable<? extends Throwable> causes, @Nullable String logLocation) {
        StringBuilder sb = new StringBuilder(message);
        int count = 0;
        for (Throwable cause : causes) {
            if (count++ < MAX_CAUSES) {
                sb.append(String.format("%n    %s", cause.getMessage()));
            }
        }

        int suppressedFailureCount = count - MAX_CAUSES;
        if (suppressedFailureCount == 1) {
            sb.append(String.format("%n    ...and %d more failure.", suppressedFailureCount));
        } else if (suppressedFailureCount > 1) {
            sb.append(String.format("%n    ...and %d more failures.", suppressedFailureCount));
        }

        if (logLocation != null) {
            sb.append(String.format("%nSee the complete log at: ")).append(logLocation);
        }
        return sb.toString();
    }
}
