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

package org.gradle.internal.concurrent;

import org.gradle.internal.Factory;
import org.jspecify.annotations.Nullable;

public interface BlockingNotifier {
    /**
     * A no-op implementation of {@link BlockingNotifier} that simply runs the action without any notification.
     */
    BlockingNotifier NO_NOTIFICATION = new BlockingNotifier() {
        @Override
        public void blocking(Runnable action) {
            action.run();
        }

        @Override
        public <T extends @Nullable Object> T blocking(Factory<T> action) {
            return action.create();
        }
    };

    /**
     * {@link #blocking(Factory)}, but returns no result.
     */
    void blocking(Runnable action);

    /**
     * Performs some blocking action, returning the result.
     * This may drop locks or otherwise allow compensating for this thread being blocked.
     */
    <T extends @Nullable Object> T blocking(Factory<T> action);
}
