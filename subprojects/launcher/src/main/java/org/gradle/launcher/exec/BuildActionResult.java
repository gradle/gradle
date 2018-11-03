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

package org.gradle.launcher.exec;

import org.gradle.tooling.internal.provider.serialization.SerializedPayload;

import javax.annotation.Nullable;
import java.io.Serializable;

/**
 * Encapsulates either a result object, or a failure as an exception, or a serialized failure.
 */
public class BuildActionResult implements Serializable {
    private final SerializedPayload result;
    private final SerializedPayload serializedFailure;
    private final RuntimeException failure;

    private BuildActionResult(SerializedPayload result, SerializedPayload serializedFailure, RuntimeException failure) {
        this.result = result;
        this.serializedFailure = serializedFailure;
        this.failure = failure;
    }

    public static BuildActionResult of(@Nullable SerializedPayload result) {
        return new BuildActionResult(result, null, null);
    }

    public static BuildActionResult failed(SerializedPayload failure) {
        return new BuildActionResult(null, failure, null);
    }

    public static BuildActionResult failed(RuntimeException failure) {
        return new BuildActionResult(null, null, failure);
    }

    @Nullable
    public SerializedPayload getResult() {
        return result;
    }

    @Nullable
    public SerializedPayload getFailure() {
        return serializedFailure;
    }

    @Nullable
    public RuntimeException getException() {
        return failure;
    }

    public boolean hasFailure() {
        return failure != null || serializedFailure != null;
    }

    public void rethrow() {
        if (failure != null) {
            throw failure;
        }
    }
}
