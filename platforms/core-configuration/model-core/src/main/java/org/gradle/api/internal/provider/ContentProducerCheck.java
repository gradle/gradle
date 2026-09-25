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

package org.gradle.api.internal.provider;

import org.gradle.api.Action;
import org.gradle.api.Task;
import org.jspecify.annotations.Nullable;

final class ContentProducerCheck {

    private static final Action<Task> FAIL_ON_UNEXECUTED = task -> {
        if (!task.getState().getExecuted()) {
            throw new UnexecutedProducerException(task);
        }
    };

    /**
     * Returns the first task producing the content of {@code provider} that has not executed yet, or
     * {@code null} when there is none.
     * <p>
     * The task is carried out by unwinding rather than captured by the visitor for two reasons. The
     * visitor stays a constant, so this costs nothing on the path where every producer has executed -
     * which is every read of every mapped provider. And the caller gets to describe the provider once
     * the evaluation scopes opened by the visit have closed; describing it during the visit would
     * render it as {@code <CIRCULAR REFERENCE>}.
     */
    @Nullable
    static Task findUnexecutedContentProducer(ProviderInternal<?> provider) {
        try {
            provider.visitContentProducerTasks(FAIL_ON_UNEXECUTED);
            return null;
        } catch (UnexecutedProducerException e) {
            return e.task;
        }
    }

    private ContentProducerCheck() {
    }

    private static final class UnexecutedProducerException extends RuntimeException {
        private final Task task;

        UnexecutedProducerException(Task task) {
            // Used for control flow only, so no message, no stack trace.
            super(null, null, false, false);
            this.task = task;
        }
    }
}
