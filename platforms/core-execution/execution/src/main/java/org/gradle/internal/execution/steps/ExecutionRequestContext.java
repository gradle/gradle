/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.internal.execution.steps;

import org.gradle.internal.execution.WorkValidationContext;

import javax.annotation.Nullable;
import java.util.Optional;

public class ExecutionRequestContext implements Context {
    private final String nonIncrementalReason;
    private final WorkValidationContext validationContext;

    public ExecutionRequestContext(@Nullable String nonIncrementalReason, WorkValidationContext validationContext) {
        this.nonIncrementalReason = nonIncrementalReason;
        this.validationContext = validationContext;
    }

    protected ExecutionRequestContext(ExecutionRequestContext parent) {
        this(parent.getNonIncrementalReason().orElse(null), parent.getValidationContext());
    }

    /**
     * If incremental mode is disabled, this returns the reason, otherwise it's empty.
     */
    Optional<String> getNonIncrementalReason() {
        return Optional.ofNullable(nonIncrementalReason);
    }

    /**
     * The validation context to use during the execution of the work.
     */
    WorkValidationContext getValidationContext() {
        return validationContext;
    }
}
