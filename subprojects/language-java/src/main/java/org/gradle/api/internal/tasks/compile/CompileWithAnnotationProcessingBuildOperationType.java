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
import java.util.List;

public class CompileWithAnnotationProcessingBuildOperationType implements BuildOperationType<CompileWithAnnotationProcessingBuildOperationType.Details, CompileWithAnnotationProcessingBuildOperationType.Result> {

    @UsedByScanPlugin
    public interface Details {
    }

    @UsedByScanPlugin
    public interface Result {

        /**
         * Returns details about the used annotation processors, if available.
         *
         * @return details about used annotation processors; {@code null} if unknown.
         */
        @Nullable
        List<AnnotationProcessorDetails> getAnnotationProcessorDetails();

        /**
         * Details about an annotation processor used during compilation.
         */
        @UsedByScanPlugin
        interface AnnotationProcessorDetails {

            /**
             * Returns the fully-qualified class name of this annotation processor.
             */
            String getClassName();

            /**
             * Returns whether this annotation processor is incremental.
             *
             * @see org.gradle.api.internal.tasks.compile.processing.IncrementalAnnotationProcessorType
             */
            boolean isIncremental();

            /**
             * Returns the total execution time of this annotation processor.
             */
            long getExecutionTimeInMillis();

        }

    }

}
