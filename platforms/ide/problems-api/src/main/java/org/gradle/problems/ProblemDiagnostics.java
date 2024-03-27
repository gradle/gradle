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

package org.gradle.problems;

import org.gradle.internal.code.UserCodeSource;
import org.gradle.internal.problems.failure.Failure;

import javax.annotation.Nullable;
import java.util.List;

/**
 * An immutable set of diagnostic information for a problem.
 */
public interface ProblemDiagnostics {

    /**
     * A failure associated with the problem.
     * <p>
     * The failure can be omitted due to limits or not be present in the first place.
     */
    @Nullable
    Failure getFailure();

    List<StackTraceElement> getMinifiedStackTrace();

    /**
     * Returns the location of the problem, if known.
     */
    @Nullable
    Location getLocation();

    /**
     * Returns the user code context when this problem was triggered.
     */
    @Nullable
    UserCodeSource getSource();
}
