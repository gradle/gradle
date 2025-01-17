/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.execution.plan;

import com.google.common.collect.ImmutableSet;

public class MutationInfo {

    public static final MutationInfo EMPTY = new MutationInfo(ImmutableSet.of(), ImmutableSet.of(), false, false, false, false);

    private final ImmutableSet<String> outputPaths;
    private final ImmutableSet<String> destroyablePaths;
    private final boolean hasFileInputs;
    private final boolean hasOutputs;
    private final boolean hasLocalState;
    private final boolean hasValidationProblem;

    public MutationInfo(
        ImmutableSet<String> outputPaths,
        ImmutableSet<String> destroyablePaths,
        boolean hasFileInputs,
        boolean hasOutputs,
        boolean hasLocalState,
        boolean hasValidationProblem
    ) {
        this.outputPaths = outputPaths;
        this.destroyablePaths = destroyablePaths;
        this.hasFileInputs = hasFileInputs;
        this.hasOutputs = hasOutputs;
        this.hasLocalState = hasLocalState;
        this.hasValidationProblem = hasValidationProblem;
    }

    public ImmutableSet<String> getOutputPaths() {
        return outputPaths;
    }

    public ImmutableSet<String> getDestroyablePaths() {
        return destroyablePaths;
    }

    public boolean hasValidationProblem() {
        return hasValidationProblem;
    }

    public boolean hasLocalState() {
        return hasLocalState;
    }

    public boolean hasOutputs() {
        return hasOutputs;
    }

    public boolean hasFileInputs() {
        return hasFileInputs;
    }

}
