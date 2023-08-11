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
import org.gradle.tooling.internal.protocol.InternalProblemEvent;
import org.gradle.tooling.internal.protocol.events.InternalProblemDescriptor;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;

@NonNullApi
public class DefaultProblemEvent extends AbstractProgressEvent<InternalProblemDescriptor> implements InternalProblemEvent {

    private String problemId;
    private String message;
    private String severity;
    private String docLink;
    private String description;
    private List<String> solutions;
    private Throwable cause;
    private Integer line;
    private Integer column;
    private String path;
    private String problemType;
    private Map<String, String> additionalData;

    public DefaultProblemEvent(
        InternalProblemDescriptor descriptor,
        String problemId,
        String message,
        String severity,
        @Nullable String path,
        @Nullable Integer line,
        @Nullable Integer column,
        @Nullable String docLink,
        @Nullable String description,
        List<String> solutions,
        @Nullable Throwable cause,
        String problemType,
        Map<String, String> additionalData
    ) {
        super(System.currentTimeMillis(), descriptor);
        this.problemId = problemId;
        this.message = message;
        this.severity = severity;
        this.path = path;
        this.line = line;
        this.column = column;
        this.docLink = docLink;
        this.description = description;
        this.solutions = solutions;
        this.cause = cause;
        this.problemType = problemType;
        this.additionalData = additionalData;
    }


    @Override
    public String getDisplayName() {
        return "Problem kdkdkd";
    }

    @Override
    public String getProblemGroup() {
        return problemId;
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
    public String getPath() {
        return path;
    }

    @Nullable
    @Override
    public Integer getLine() {
        return line;
    }

    @Nullable
    @Override
    public Integer getColumn() {
        return column;
    }

    @Nullable
    @Override
    public String getDocumentationLink() {
        return docLink;
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

    public Map<String, String> getAdditionalData() {
        return additionalData;
    }
}
