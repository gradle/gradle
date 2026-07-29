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

package org.gradle.internal.work;

import javax.annotation.concurrent.ThreadSafe;

@ThreadSafe
public interface SubmissionQueue {
    void add(Runnable task);

    /**
     * Process work from this queue on the current thread until the queue is empty.
     *
     * <p>
     * The caller must stop submitting to this queue before draining it, otherwise concurrent
     * {@link #add(Runnable)} calls can keep the current thread here indefinitely. Work already
     * submitted may still be running on other threads when this returns; this only guarantees
     * that nothing is left queued.
     */
    void processWorkUsingCurrentThreadUntilEmpty();
}
