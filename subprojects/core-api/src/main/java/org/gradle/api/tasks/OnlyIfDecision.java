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

package org.gradle.api.tasks;

import org.gradle.api.Incubating;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * The decision of an {@code onlyIf} predicate on whether a task should run, along with the reason for skipping it.
 * <p>
 * Use it as the value of a {@link org.gradle.api.provider.Provider} passed to {@link org.gradle.api.Task#onlyIf(org.gradle.api.provider.Provider)},
 * so that the reason for skipping a task is computed together with the decision itself.
 *
 * <pre>
 * tasks.register("deploy") {
 *     onlyIf(providers.environmentVariable("DEPLOY_TOKEN").map { OnlyIfDecision.proceed() }
 *         .orElse(OnlyIfDecision.skip("DEPLOY_TOKEN is not set")))
 * }
 * </pre>
 *
 * @see org.gradle.api.Task#onlyIf(org.gradle.api.provider.Provider)
 * @since 9.10.0
 */
@Incubating
public final class OnlyIfDecision {
    private static final OnlyIfDecision PROCEED = new OnlyIfDecision(true, null);
    private static final OnlyIfDecision SKIP_WITHOUT_REASON = new OnlyIfDecision(false, null);

    private final boolean shouldRun;
    @Nullable
    private final String skipReason;

    private OnlyIfDecision(boolean shouldRun, @Nullable String skipReason) {
        this.shouldRun = shouldRun;
        this.skipReason = skipReason;
    }

    /**
     * Returns a decision to run the task.
     *
     * @since 9.10.0
     */
    public static OnlyIfDecision proceed() {
        return PROCEED;
    }

    /**
     * Returns a decision to skip the task without giving a reason.
     * <p>
     * If you never report a reason, a {@code Provider<Boolean>} passed to {@link org.gradle.api.Task#onlyIf(org.gradle.api.provider.Provider)} is simpler.
     *
     * @since 9.10.0
     */
    public static OnlyIfDecision skip() {
        return SKIP_WITHOUT_REASON;
    }

    /**
     * Returns a decision to skip the task for the given reason.
     * <p>
     * A {@code null} or blank reason is the same as {@link #skip()}.
     *
     * @param reason the reason for skipping the task, reported in the build output and build scan
     * @since 9.10.0
     */
    public static OnlyIfDecision skip(@Nullable String reason) {
        if (reason == null || reason.trim().isEmpty()) {
            return SKIP_WITHOUT_REASON;
        }
        return new OnlyIfDecision(false, reason);
    }

    /**
     * Returns {@code true} if the task should run.
     *
     * @since 9.10.0
     */
    public boolean shouldRun() {
        return shouldRun;
    }

    /**
     * Returns the reason for skipping the task, or {@code null} if the task should run or no reason was given.
     *
     * @since 9.10.0
     */
    @Nullable
    public String getSkipReason() {
        return skipReason;
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        OnlyIfDecision that = (OnlyIfDecision) o;
        return shouldRun == that.shouldRun && Objects.equals(skipReason, that.skipReason);
    }

    @Override
    public int hashCode() {
        return Objects.hash(shouldRun, skipReason);
    }

    @Override
    public String toString() {
        if (shouldRun) {
            return "OnlyIfDecision.proceed()";
        }
        return skipReason == null ? "OnlyIfDecision.skip()" : "OnlyIfDecision.skip('" + skipReason + "')";
    }
}
