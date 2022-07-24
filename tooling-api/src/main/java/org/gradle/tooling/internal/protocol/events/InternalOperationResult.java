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

package org.gradle.tooling.internal.protocol.events;

import org.gradle.tooling.internal.protocol.InternalFailure;
import org.gradle.tooling.internal.protocol.InternalProtocolInterface;

import java.util.List;

/**
 * DO NOT CHANGE THIS INTERFACE. It is part of the cross-version protocol.
 *
 * @since 2.5
 */
public interface InternalOperationResult extends InternalProtocolInterface {
    /**
     * Returns the time the build execution started.
     *
     * @return The start time of the build
     */
    long getStartTime();

    /**
     * Returns the time the result was produced.
     *
     * @return The time the result was produced.
     */
    long getEndTime();

    /**
     * Returns the failures that occurred while running the build, if any.
     *
     * @return The failures that occurred
     */
    List<? extends InternalFailure> getFailures();
}
