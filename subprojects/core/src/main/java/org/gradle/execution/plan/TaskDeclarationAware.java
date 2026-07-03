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

package org.gradle.execution.plan;

import org.gradle.api.Task;

/**
 * Implemented by work graph {@link Node}s that want to know which tasks transitively
 * declared them as inputs.
 * <p>
 * After the execution plan is built, the plan walks dependency successors transitively
 * from each {@link LocalTaskNode} and invokes {@link #markDeclaredBy(Task)} on every
 * reachable {@code TaskDeclarationAware} node. This captures attribution that the
 * per-node resolver path cannot, including cross-set relationships such as
 * {@code @InputArtifactDependencies} feeding from one transform to another.
 */
public interface TaskDeclarationAware {
    /**
     * Records that the given task transitively declared this node as an input.
     */
    void markDeclaredBy(Task task);
}
