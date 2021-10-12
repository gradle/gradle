/*
 * Copyright 2020 the original author or authors.
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

import org.gradle.internal.file.Stat;
import org.gradle.internal.service.scopes.Scopes;
import org.gradle.internal.service.scopes.ServiceScope;
import org.gradle.internal.snapshot.CaseSensitivity;

@ServiceScope(Scopes.Build.class)
public class ExecutionNodeAccessHierarchies {
    private final ExecutionNodeAccessHierarchy outputHierarchy;
    private final ExecutionNodeAccessHierarchy destroyableHierarchy;
    private final CaseSensitivity caseSensitivity;
    private final Stat stat;

    public ExecutionNodeAccessHierarchies(CaseSensitivity caseSensitivity, Stat stat) {
        this.caseSensitivity = caseSensitivity;
        this.stat = stat;
        outputHierarchy = new ExecutionNodeAccessHierarchy(caseSensitivity, stat);
        destroyableHierarchy = new ExecutionNodeAccessHierarchy(caseSensitivity, stat);
    }

    /**
     * Create the input node access hierarchy.
     *
     * For performance reasons, we keep one input node access hierarchy per project,
     * so we only have a factory method here and this is used to create the hierarchy in {@link org.gradle.execution.ProjectExecutionServices}.
     */
    public InputNodeAccessHierarchy createInputHierarchy() {
        return new InputNodeAccessHierarchy(caseSensitivity, stat);
    }

    public ExecutionNodeAccessHierarchy getOutputHierarchy() {
        return outputHierarchy;
    }

    public ExecutionNodeAccessHierarchy getDestroyableHierarchy() {
        return destroyableHierarchy;
    }

    public static class InputNodeAccessHierarchy extends ExecutionNodeAccessHierarchy {
        public InputNodeAccessHierarchy(CaseSensitivity caseSensitivity, Stat stat) {
            super(caseSensitivity, stat);
        }
    }
}
