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

import org.gradle.api.internal.tasks.compile.incremental.processing.IncrementalAnnotationProcessorType;
import org.gradle.internal.operations.BuildOperationType;
import org.gradle.internal.scan.NotUsedByScanPlugin;

import javax.annotation.Nullable;
import java.util.List;

/**
 * @since 5.1
 */
@NotUsedByScanPlugin("used to report annotation processor execution times to TAPI progress listeners")
public class CompileJavaBuildOperationType implements BuildOperationType<CompileJavaBuildOperationType.Details, CompileJavaBuildOperationType.Result> {

    public interface Details {
    }

    public interface Result {

        /**
         * Returns details about the used annotation processors, if available.
         *
         * <p>Details are only available if an instrumented compiler was used.
         *
         * @return details about used annotation processors; {@code null} if unknown.
         */
        @Nullable
        List<AnnotationProcessorDetails> getAnnotationProcessorDetails();

        /**
         * Details about an annotation processor used during compilation.
         */
        interface AnnotationProcessorDetails {

            /**
             * Returns the fully-qualified class name of this annotation processor.
             */
            String getClassName();

            /**
             * Returns the type of this annotation processor.
             */
            Type getType();

            /**
             * Returns the total execution time of this annotation processor.
             */
            long getExecutionTimeInMillis();

            /**
             * Type of annotation processor.
             *
             * @see IncrementalAnnotationProcessorType
             */
            enum Type {
                ISOLATING, AGGREGATING, UNKNOWN
            }

        }

    }

}
