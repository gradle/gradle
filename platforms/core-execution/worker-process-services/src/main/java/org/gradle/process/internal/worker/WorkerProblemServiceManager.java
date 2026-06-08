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

package org.gradle.process.internal.worker;

import org.gradle.api.problems.internal.ProblemsInternal;
import org.jspecify.annotations.NullMarked;

/**
 * Daemon-side lifecycle for the problems service used to dispatch problems received from a worker.
 * <p>
 * Kept separate from {@link org.gradle.process.internal.worker.problem.WorkerProblemProtocol} so that these
 * lifecycle methods do not leak into the worker-to-daemon wire protocol.
 */
@NullMarked
public interface WorkerProblemServiceManager {
    /**
     * Binds the problems service to use for reporting problems received from the worker.
     *
     * @throws NullPointerException if {@code problems} is {@code null}
     * @throws IllegalStateException if a service is already bound (the previous worker run did not clear it)
     */
    void bindProblemsService(ProblemsInternal problems);

    /**
     * Clears any previously bound problems service.
     */
    void clearProblemsService();
}
