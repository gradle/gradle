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

package org.gradle.api.internal.tasks.compile;

import org.gradle.internal.operations.BuildOperationType;
import org.gradle.internal.scan.UsedByScanPlugin;

import javax.annotation.Nullable;
import java.util.Map;

public class CompileWithAnnotationProcessingBuildOperationType implements BuildOperationType<CompileWithAnnotationProcessingBuildOperationType.Details, CompileWithAnnotationProcessingBuildOperationType.Result> {

    @UsedByScanPlugin
    public interface Details {
    }

    @UsedByScanPlugin
    public interface Result {

        /**
         * Returns the total execution time in milliseconds by each annotation processor, if available.
         *
         * @return execution time by annotation processor; {@code null} if unknown.
         */
        @Nullable
        Map<String, Long> getExecutionTimeByAnnotationProcessor();

    }

}
