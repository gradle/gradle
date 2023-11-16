/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.internal.build.event.types;

import org.gradle.api.NonNullApi;
import org.gradle.tooling.internal.protocol.problem.InternalAdditionalData;
import org.gradle.tooling.internal.protocol.problem.InternalBasicProblemDetails;
import org.gradle.tooling.internal.protocol.problem.InternalDetails;
import org.gradle.tooling.internal.protocol.problem.InternalLabel;
import org.gradle.tooling.internal.protocol.problem.InternalProblemCategory;
import org.gradle.tooling.internal.protocol.problem.InternalSeverity;

import javax.annotation.Nullable;
import java.io.Serializable;

@NonNullApi
public class DefaultProblemDetails implements InternalBasicProblemDetails, Serializable {
    private final String json;
    private Throwable exception;
    private final InternalProblemCategory category;
    private final InternalLabel label;
    private final InternalDetails details;

    private final InternalSeverity severity;
    private final InternalAdditionalData additionalData;

    public DefaultProblemDetails(
        String json,
        InternalProblemCategory category,
        InternalLabel label,
        @Nullable InternalDetails details,
        InternalSeverity severity,
        InternalAdditionalData additionalData,
        Throwable exception
    ) {
        this.json = json;
        this.category = category;
        this.label = label;
        this.details = details;
        this.severity = severity;
        this.additionalData = additionalData;
        this.exception = exception;
    }
    @Override
    public String getJson() {
        return json;
    }
    @Override
    public InternalProblemCategory getCategory() {
        return category;
    }

    @Override
    public InternalLabel getLabel() {
        return label;
    }

    @Override
    public InternalDetails getDetails() {
        return details;
    }

    @Override
    public InternalSeverity getSeverity() {
        return severity;
    }

    @Override
    public InternalAdditionalData getAdditionalData() {
        return additionalData;
    }

    @Override
    public Throwable getException() {
        return exception;
    }
}
