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

import org.gradle.util.Path;

public class ExecuteScheduledTransformationStepBuildOperationDetails implements ExecuteScheduledTransformationStepBuildOperationType.Details {

    private final Path buildPath;
    private final TransformationIdentity transformationIdentity;
    private final String transformerName;
    private final String subjectName;

    public ExecuteScheduledTransformationStepBuildOperationDetails(Path buildPath, TransformationIdentity transformationIdentity, String transformerName, String subjectName) {
        this.buildPath = buildPath;
        this.transformationIdentity = transformationIdentity;
        this.transformerName = transformerName;
        this.subjectName = subjectName;
    }

    public Path getBuildPath() {
        return buildPath;
    }

    public TransformationIdentity getTransformationIdentity() {
        return transformationIdentity;
    }

    @Override
    public String getBuildPathString() {
        return buildPath.getPath();
    }

    @Override
    public long getTransformationId() {
        return transformationIdentity.getId();
    }

    @Override
    public String getTransformerName() {
        return transformerName;
    }

    @Override
    public String getSubjectName() {
        return subjectName;
    }
}
