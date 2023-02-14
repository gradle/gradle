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

package org.gradle.api.internal.artifacts.transform;

import org.gradle.internal.taskgraph.CalculateTaskGraphBuildOperationType;
import org.gradle.internal.taskgraph.NodeIdentity;

import java.util.List;

/**
 * A {@link CalculateTaskGraphBuildOperationType.PlannedNode} for a {@link TransformationNode}.
 */
public class DefaultPlannedTransform implements CalculateTaskGraphBuildOperationType.PlannedNode {

    private final TransformationIdentity transformationIdentity;
    private final List<? extends NodeIdentity> dependencies;

    public DefaultPlannedTransform(
        TransformationIdentity transformationIdentity,
        List<? extends NodeIdentity> dependencies
    ) {
        this.transformationIdentity = transformationIdentity;
        this.dependencies = dependencies;
    }

    @Override
    public TransformationIdentity getNodeIdentity() {
        return transformationIdentity;
    }

    @Override
    public List<? extends NodeIdentity> getNodeDependencies() {
        return dependencies;
    }

    @Override
    public String toString() {
        return transformationIdentity.toString();
    }
}
