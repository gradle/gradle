/*
 * Copyright 2015 the original author or authors.
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
package org.gradle.tooling.internal.protocol;

import org.gradle.api.Incubating;

import java.util.List;

/**
 * DO NOT CHANGE THIS INTERFACE. It is part of the cross-version protocol.
 *
 * @since 2.4
 */
public interface InternalFailure {

    /**
     * The message of the failure, if any.
     *
     * @return the failure message, can be null
     */
    String getMessage();

    /**
     * The description of the failure, if any.
     *
     * @return the failure description, can be null
     */
    String getDescription();

    /**
     * The cause of the failure, if any, which is again a failure.
     *
     * @return the cause of the failure, can be null
     */
    List<? extends InternalFailure> getCauses();

    /**
     * The problems that occurred during the operation, if any.
     *
     * @return the problems that occurred during the operation, can be null
     * @since 8.12
     */
    @Incubating
    List<InternalBasicProblemDetailsVersion3> getProblems();

}
