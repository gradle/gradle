/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.api.internal.tasks;

import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.project.taskfactory.TaskIdentity;
import org.gradle.api.tasks.VerificationException;
import org.gradle.internal.code.UserCodeSource;

import java.util.Optional;

/**
 * Utility class for computing task provenance information.
 * <p>
 * Extracted from {@link TaskInternal} default methods to avoid leaking
 * implementation details into Gradle's public API surface
 * ({@code DefaultTask -> AbstractTask -> TaskInternal}).
 */
public final class TaskProvenanceUtil {
    private TaskProvenanceUtil() {}

    /**
     * Compute a human-readable provenance string for a task based on where it was registered.
     *
     * @param task the task to compute provenance for
     * @return an {@link Optional} containing the provenance description, or empty if the source is unknown
     */
    public static Optional<String> getProvenance(TaskInternal task) {
        TaskIdentity<?> identity = task.getTaskIdentity();
        UserCodeSource source = identity.getUserCodeSource();
        if (source == null) {
            return Optional.empty();
        }

        String sourceDesc = source.getDisplayName().getDisplayName();
        String preposition = source instanceof UserCodeSource.Binary ? "by" : "in";
        return Optional.of(String.format("registered %s %s", preposition, sourceDesc));
    }

    /**
     * Build a failure message for display when a task fails execution.
     * <p>
     * Includes task provenance information unless the failure is a {@link VerificationException}.
     *
     * @param task  the failing task
     * @param cause the cause of the failure
     * @return the constructed failure message
     */
    public static String buildFailureMessage(TaskInternal task, Throwable cause) {
        boolean isVerificationFailure = cause instanceof VerificationException;
        String createdBy = "";
        if (!isVerificationFailure) {
            String provenance = getProvenance(task).orElse(null);
            if (provenance != null) {
                createdBy = String.format(" (%s)", provenance);
            }
        }
        return String.format("Execution failed for %s%s.", task, createdBy);
    }
}
