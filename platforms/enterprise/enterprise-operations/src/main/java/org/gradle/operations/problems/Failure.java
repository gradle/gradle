/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.operations.problems;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * A failure reported on build operations.
 *
 * The main reason to have this type is so we can query failures
 * for metadata and associated problems.
 *
 * @since 9.0
 */
public interface Failure {

    /**
     * The class name of the original throwable.
     *
     * @since 9.0
     */
    String getExceptionType();

    /**
     * The message of the failure.
     *
     * @since 9.0
     */
    @Nullable
    String getMessage();

    /**
     * The metadata of the failure.
     *
     * @since 9.0
     */
    Map<String, String> getMetadata();

    /**
     * The stack trace of the failure.
     *
     * @since 9.0
     */
    List<StackTraceElement> getStackTrace();

    /**
     * The class level annotations of the underlying exception class.
     *
     * @since 9.0
     */
    List<String> getClassLevelAnnotations();

    /**
     * The causes of this failure.
     *
     * @since 9.0
     */
    List<Failure> getCauses();

    /**
     * Problems associated with this failure.
     *
     * @since 9.0
     */
    List<Problem> getProblems();
}
