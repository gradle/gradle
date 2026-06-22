/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.internal.operations.trace;

import jdk.jfr.Category;
import jdk.jfr.Event;
import jdk.jfr.Label;
import jdk.jfr.Name;
import jdk.jfr.StackTrace;
import jdk.jfr.Timestamp;

/**
 * JFR event describing a single Gradle build operation.
 */
@Name(BuildOperationJfrEvent.NAME)
@Label("Build Operation")
@Category({"Gradle", "Build Operations"})
@StackTrace(false)
class BuildOperationJfrEvent extends Event {

    /**
     * The name of the JFR event type.
     */
    static final String NAME = "org.gradle.BuildOperation";

    /**
     * Unique ID of the operation
     */
    @Label("Operation ID")
    long operationId;

    /**
     * ID of the parent operation, or 0 if this is a root operation
     */
    @Label("Parent Operation ID")
    long parentId; // 0 = root operation

    /**
     * Human-readable description of the operation, e.g. "Compile Java source 'Main.java'"
     */
    @Label("Display Name")
    String displayName;

    /**
     * Gradle's own timestamp when the operation started.
     */
    @Label("Gradle Start Time")
    @Timestamp
    long gradleStartTime;

    /**
     * Gradle's own timestamp when the operation ended.
     */
    @Label("Gradle End Time")
    @Timestamp
    long gradleEndTime;

    /**
     * {@code true} if the operation threw an exception, {@code false} otherwise
     */
    @Label("Failed")
    boolean failed;

    /**
     * Message from the exception, or null
     */
    @Label("Failure Message")
    String failureMessage;

    /**
     * FQN of the exception class, or null
     */
    @Label("Failure Type")
    String failureType;
}
