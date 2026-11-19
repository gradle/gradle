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

package org.gradle.api.internal.artifacts.transform;

import org.gradle.internal.Try;
import org.gradle.internal.execution.DeferredExecution;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/// A step or chain invocation whose result may be known without invoking,
/// from the identity cache or otherwise.
final class TransformInvocation {
    private final @Nullable Try<TransformStepSubject> knownResult;
    private final @Nullable Supplier<Try<TransformStepSubject>> work;

    private TransformInvocation(@Nullable Try<TransformStepSubject> knownResult, @Nullable Supplier<Try<TransformStepSubject>> work) {
        this.knownResult = knownResult;
        this.work = work;
    }

    /// Creates an invocation whose result is known without invoking it.
    static TransformInvocation known(Try<TransformStepSubject> result) {
        return new TransformInvocation(result, null);
    }

    /// Creates an invocation from a deferred execution, known after a cache hit.
    static TransformInvocation of(DeferredExecution<TransformStepSubject> execution) {
        return execution.getCacheHit()
            .map(TransformInvocation::known)
            .orElseGet(() -> new TransformInvocation(null, execution::execute));
    }

    /// Creates an invocation that runs the given work each time it is invoked.
    static TransformInvocation deferred(Supplier<Try<TransformStepSubject>> work) {
        return new TransformInvocation(null, work);
    }

    /// The result, if known without invoking; [#invoke()] never sets it.
    Optional<Try<TransformStepSubject>> getResultWithoutInvoking() {
        return Optional.ofNullable(knownResult);
    }

    /// Returns the known result, or runs the work.
    Try<TransformStepSubject> invoke() {
        Try<TransformStepSubject> result = knownResult;
        return result != null ? result : Objects.requireNonNull(work).get();
    }
}
