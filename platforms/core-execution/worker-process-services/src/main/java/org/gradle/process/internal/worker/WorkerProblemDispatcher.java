/*
 * Copyright 2023 the original author or authors.
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

import org.gradle.api.problems.Problem;
import org.gradle.api.problems.internal.ProblemsInternal;
import org.gradle.internal.operations.OperationIdentifier;
import org.gradle.process.internal.worker.problem.WorkerProblemProtocol;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * Daemon-side hub for the worker-to-daemon problems flow.
 * <p>
 * Owns the currently-bound {@link ProblemsInternal} service (see {@link WorkerProblemServiceManager}) and
 * dispatches incoming {@link WorkerProblemProtocol#reportProblem report} calls from the worker to it.
 * <p>
 * The class is not responsible for automatic lifecycle management of the problems service.
 */
@NullMarked
public class WorkerProblemDispatcher implements WorkerProblemProtocol, WorkerProblemServiceManager {

    private volatile @Nullable ProblemsInternal problems;

    @Override
    public void bindProblemsService(ProblemsInternal problems) {
        Objects.requireNonNull(problems, "Problems service must not be null");
        if (this.problems != null) {
            throw new IllegalStateException("A problems service is already bound; previous worker run did not clear it");
        }
        this.problems = problems;
    }

    @Override
    public void clearProblemsService() {
        this.problems = null;
    }

    @Override
    public void reportProblem(Problem problem, OperationIdentifier id) {
        // Read once into a local to avoid a NPE if another thread clears the binding between the null check and the dereference.
        ProblemsInternal p = problems;
        // Secondary per-report null check: if this happens, our lifecycle management has failed somewhere
        if (p == null) {
            throw new IllegalStateException("Received a problem from the worker, but no problems service is currently bound to the protocol");
        }
        p.getInternalReporter().report(problem, id);
    }
}
