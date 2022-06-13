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
 * Represents an exception that can be combined with other exceptions during end-of-build reporting
 * in order to provide a more detailed summary of the problem, should multiple related exceptions be
 * thrown during the course of a build.
 */
public interface MergeableException {
    /**
     * A mergeable exception knows how to return a merger which can combine it with other, identically-typed exceptions.
     * @return merger for this type of exception
     */
    ExceptionMerger<? extends MergeableException, ? extends MultiCauseException> getMerger();
}
