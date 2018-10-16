/*
 * Copyright 2018 the original author or authors.
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

import org.gradle.execution.ProjectExecutionServiceRegistry;
import org.gradle.execution.plan.Node;
import org.gradle.execution.plan.NodeExecutor;
import org.gradle.internal.operations.BuildOperationExecutor;

public class TransformationNodeExecutor implements NodeExecutor {
    private final BuildOperationExecutor buildOperationExecutor;
    private final ArtifactTransformListener transformListener;

    public TransformationNodeExecutor(BuildOperationExecutor buildOperationExecutor, ArtifactTransformListener transformListener) {
        this.buildOperationExecutor = buildOperationExecutor;
        this.transformListener = transformListener;
    }

    @Override
    public boolean execute(Node node, ProjectExecutionServiceRegistry services) {
        if (node instanceof TransformationNode) {
            TransformationNode transformationNode = (TransformationNode) node;
            transformationNode.execute(buildOperationExecutor, transformListener);
            return true;
        } else {
            return false;
        }
    }
}
