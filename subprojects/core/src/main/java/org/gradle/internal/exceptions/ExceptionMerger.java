/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.internal.exceptions;

/**
 * Specifies a type that can consolidate the information in muliple {@link MergeableException}
 * instances into a single exception.
 *
 * @param <E> type which can be merged
 * @param <R> result type of merging
 */
public interface ExceptionMerger<E extends MergeableException, R extends Throwable & MultiCauseException> {
    /**
     * Adds an exception to the list of exceptions to be merged.
     *
     * @param exception the exception to add
     */
    void merge(E exception);

    /**
     * Gets the result of merging the given exceptions into a single exception.
     *
     * @return the merged exception
     */
    R getMergedException();

    /**
     * Get the type of exception which can be merged.
     *
     * @return the type of exception which can be merged
     */
    Class<E> getMergeableExceptionType();

    /**
     * Get the type of exception which will be returned by {@link #getMergedException()}.
     *
     * @return the type of exception which will be returned by {@link #getMergedException()}
     */
    Class<R> getMergedExceptionType();
}
