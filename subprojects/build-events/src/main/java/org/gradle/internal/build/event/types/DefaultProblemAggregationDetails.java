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
import org.gradle.tooling.internal.protocol.InternalProblemAggregationDetailsV2;
import org.gradle.tooling.internal.protocol.InternalProblemContextDetails;
import org.gradle.tooling.internal.protocol.problem.InternalDocumentationLink;
import org.gradle.tooling.internal.protocol.problem.InternalLabel;
import org.gradle.tooling.internal.protocol.problem.InternalProblemCategory;
import org.gradle.tooling.internal.protocol.problem.InternalSeverity;

import javax.annotation.Nullable;
import java.io.Serializable;
import java.util.List;

@NonNullApi
public class DefaultProblemAggregationDetails implements InternalProblemAggregationDetailsV2, Serializable {

    private final InternalLabel label;
    private final InternalProblemCategory category;
    private final InternalSeverity severity;
    private final InternalDocumentationLink documentationLink;
    private final List<InternalProblemContextDetails> problems;

    public DefaultProblemAggregationDetails(InternalLabel label,
                                            InternalProblemCategory category,
                                            InternalSeverity severity,
                                            InternalDocumentationLink documentationLink,
                                            List<InternalProblemContextDetails> problems) {
        this.label = label;
        this.category = category;
        this.severity = severity;
        this.documentationLink = documentationLink;
        this.problems = problems;
    }

    @Override
    public String getJson() {
        return "{}";
    }

    @Nullable
    @Override
    public InternalDocumentationLink getDocumentationLink() {
        return documentationLink;
    }

    @Override
    public InternalSeverity getSeverity() {
        return severity;
    }

    @Override
    public List<InternalProblemContextDetails> getProblems() {
        return problems;
    }

    @Override
    public InternalLabel getLabel() {
        return label;
    }

    @Override
    public InternalProblemCategory getCategory() {
        return category;
    }
}
