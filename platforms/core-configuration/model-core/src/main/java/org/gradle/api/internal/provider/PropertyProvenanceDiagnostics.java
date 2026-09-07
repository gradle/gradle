/*
 * Copyright 2026 Gradle and contributors.
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

package org.gradle.api.internal.provider;

import org.gradle.api.internal.provenance.EffectiveProvenanceView;
import org.gradle.api.internal.provenance.FailedOperation;
import org.gradle.api.internal.provenance.ProvenanceRenderer;

/** Failure-only exception decoration, retaining the original failure as the direct cause. */
public final class PropertyProvenanceDiagnostics {
    private PropertyProvenanceDiagnostics() {
    }

    public static MissingValueException missing(MissingValueException failure, EffectiveProvenanceView view) {
        if (failure instanceof ReportedFailure) {
            return failure;
        }
        return new ReportedMissingValue(failure.getMessage() + "\n\n" + ProvenanceRenderer.failure(view, null), failure);
    }

    public static RuntimeException mutation(RuntimeException failure, EffectiveProvenanceView view, FailedOperation operation) {
        if (failure instanceof ReportedFailure) {
            return failure;
        }
        if (failure instanceof IllegalArgumentException) {
            return new ReportedIllegalArgument(failure.getMessage() + "\n\n" + ProvenanceRenderer.failure(view, operation), failure);
        }
        if (failure instanceof IllegalStateException) {
            return new ReportedIllegalState(failure.getMessage() + "\n\n" + ProvenanceRenderer.failure(view, operation), failure);
        }
        // Transform failures and other evaluation coverage belong to D3.
        return failure;
    }

    private interface ReportedFailure {
    }

    private static final class ReportedMissingValue extends MissingValueException implements ReportedFailure {
        private ReportedMissingValue(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private static final class ReportedIllegalArgument extends IllegalArgumentException implements ReportedFailure {
        private ReportedIllegalArgument(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private static final class ReportedIllegalState extends IllegalStateException implements ReportedFailure {
        private ReportedIllegalState(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
