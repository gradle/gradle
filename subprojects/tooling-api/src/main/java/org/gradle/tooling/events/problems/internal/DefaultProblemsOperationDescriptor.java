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

package org.gradle.tooling.events.problems.internal;

import org.gradle.api.NonNullApi;
import org.gradle.tooling.events.OperationDescriptor;
import org.gradle.tooling.events.internal.DefaultOperationDescriptor;
import org.gradle.tooling.events.problems.ProblemDescriptor;
import org.gradle.tooling.internal.protocol.events.InternalOperationDescriptor;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;

@NonNullApi
public class DefaultProblemsOperationDescriptor extends DefaultOperationDescriptor implements ProblemDescriptor {
    private final String problemGroup;
    private final String severity;
    private final String message;
    private final String description;
    private final List<String> solutions;
    @Nullable
    private final String path;
    @Nullable
    private final Integer line;
    private final String documentationLink;
    private final Throwable cause;
    private final String problemType;
    private Map<String, String> additionalMetaData;

    public DefaultProblemsOperationDescriptor(
        InternalOperationDescriptor internalDescriptor,
        OperationDescriptor parent,
        String problemId,
        String severity,
        String message,
        @Nullable String description,
        List<String> solutions,
        @Nullable String path,
        @Nullable Integer line,
        @Nullable String documentationLink,
        @Nullable Throwable cause,
        String problemType,
        Map<String, String> additionalMetaData
    ) {
        super(internalDescriptor, parent);
        this.problemGroup = problemId;
        this.severity = severity;
        this.message = message;
        this.description = description;
        this.solutions = solutions;
        this.path = path;
        this.line = line;
        this.documentationLink = documentationLink;
        this.cause = cause;
        this.problemType = problemType;
        this.additionalMetaData = additionalMetaData;
    }

    @Override
    public String getProblemGroup() {
        return problemGroup;
    }

    @Override
    public String getMessage() {
        return message;
    }

    @Override
    public String getSeverity() {
        return severity;
    }

    @Nullable
    @Override
    public String getDocumentationLink() {
        return documentationLink;
    }

    @Nullable
    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public List<String> getSolutions() {
        return solutions;
    }

    @Nullable
    @Override
    public Throwable getCause() {
        return cause;
    }

    @Override
    public String getProblemType() {
        return problemType;
    }

    @Nullable
    @Override
    public Integer getLine() {
        return line;
    }

    @Nullable
    @Override
    public String getPath() {
        return path;
    }

    @Override
    public Map<String, String> getAdditionalMetaData() {
        return additionalMetaData;
    }
}
