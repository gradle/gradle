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

package org.gradle.process.internal.streams;

import org.jspecify.annotations.NullMarked;

/**
 * A {@link StreamsHandler} that can report when its asynchronous stream work has finished.
 */
@NullMarked
public interface FinishNotifyingStreamsHandler extends StreamsHandler {
    /**
     * Runs the given callback exactly once, after all asynchronous stream work has finished.
     *
     * <p>The callback runs on the thread that finishes the last piece of stream work, or on the
     * calling thread if that work already finished.
     */
    void whenStreamsFinished(Runnable callback);
}
