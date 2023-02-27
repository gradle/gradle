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

import org.gradle.internal.operations.BuildOperationType;
import org.gradle.internal.scan.NotUsedByScanPlugin;

import java.util.Map;

/**
 * A {@link BuildOperationType} for executing a scheduled transformation step.
 * <p>
 * Encompasses the execution of a transformation node.
 * A transformation node runs only one transformation step, though possibly on multiple files.
 */
public class ExecuteScheduledTransformationStepBuildOperationType implements BuildOperationType<ExecuteScheduledTransformationStepBuildOperationType.Details, ExecuteScheduledTransformationStepBuildOperationType.Result> {

    public interface Details {

        TransformationIdentity getTransformationIdentity();

        Map<String, String> getSourceAttributes();

        Map<String, String> getFromAttributes();

        Map<String, String> getToAttributes();

        Class<?> getTransformType();

        /**
         * Returns the display name of the transformer.
         *
         * Not used by build scans but for TAPI events in {@code TransformOperationMapper}.
         */
        @NotUsedByScanPlugin
        String getTransformerName();

        /**
         * Returns the display name of the transformation subject.
         *
         * Not used by build scans but for TAPI events in {@code TransformOperationMapper}.
         */
        @NotUsedByScanPlugin
        String getSubjectName();

    }

    public interface Result {
    }
}
