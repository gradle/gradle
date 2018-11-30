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

import org.gradle.internal.operations.BuildOperationType;
import org.gradle.internal.scan.NotUsedByScanPlugin;

/**
 * @since 5.1
 */
@NotUsedByScanPlugin
public class ExecuteScheduledTransformationStepBuildOperationType implements BuildOperationType<ExecuteScheduledTransformationStepBuildOperationType.Details, ExecuteScheduledTransformationStepBuildOperationType.Result> {

    public interface Details {

        /**
         * Returns the display name of the transformer.
         */
        String getTransformerName();

        /**
         * Returns the display name of the transformation subject.
         */
        String getSubjectName();

    }

    public interface Result {
    }

    static final Result RESULT = new Result() {
    };

}
