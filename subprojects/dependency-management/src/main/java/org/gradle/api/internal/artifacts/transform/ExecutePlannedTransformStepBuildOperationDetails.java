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

import com.google.common.collect.ImmutableMap;
import org.gradle.internal.operations.trace.CustomOperationTraceSerialization;
import org.gradle.operations.dependencies.transforms.ExecutePlannedTransformStepBuildOperationType;
import org.gradle.operations.dependencies.transforms.PlannedTransformStepIdentity;

public class ExecutePlannedTransformStepBuildOperationDetails implements ExecutePlannedTransformStepBuildOperationType.Details, CustomOperationTraceSerialization {

    private final TransformationNode transformationNode;
    private final String transformerName;
    private final String subjectName;

    public ExecutePlannedTransformStepBuildOperationDetails(TransformationNode transformationNode, String transformerName, String subjectName) {
        this.transformationNode = transformationNode;
        this.transformerName = transformerName;
        this.subjectName = subjectName;
    }

    public TransformationNode getTransformationNode() {
        return transformationNode;
    }

    @Override
    public PlannedTransformStepIdentity getPlannedTransformStepIdentity() {
        return transformationNode.getNodeIdentity();
    }

    @Override
    public Class<?> getTransformActionClass() {
        return transformationNode.getTransformationStep().getTransformer().getImplementationClass();
    }

    @Override
    public String getTransformerName() {
        return transformerName;
    }

    @Override
    public String getSubjectName() {
        return subjectName;
    }

    @Override
    public Object getCustomOperationTraceSerializableModel() {
        ImmutableMap.Builder<String, Object> builder = new ImmutableMap.Builder<>();
        builder.put("plannedTransformStepIdentity", getPlannedTransformStepIdentity());
        builder.put("transformActionClass", getTransformActionClass());
        builder.put("transformerName", transformerName);
        builder.put("subjectName", subjectName);
        return builder.build();
    }

}
