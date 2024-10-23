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
import org.gradle.tooling.internal.protocol.InternalProblemDefinition;
import org.gradle.tooling.internal.protocol.InternalProblemId;
import org.gradle.tooling.internal.protocol.problem.InternalDocumentationLink;
import org.gradle.tooling.internal.protocol.problem.InternalSeverity;

import javax.annotation.Nullable;
import java.io.Serializable;

@NonNullApi
public class DefaultProblemDefinition implements InternalProblemDefinition, Serializable {

    private final InternalProblemId id;

    private final InternalSeverity severity;

    private final InternalDocumentationLink documentationLink;

    public DefaultProblemDefinition(InternalProblemId id, InternalSeverity severity, @Nullable InternalDocumentationLink documentationLink) {
        this.id = id;
        this.severity = severity;
        this.documentationLink = documentationLink;
    }

    @Override
    public InternalProblemId getId() {
        return id;
    }

    @Override
    public InternalSeverity getSeverity() {
        return severity;
    }

    @Override
    public InternalDocumentationLink getDocumentationLink() {
        return documentationLink;
    }
}
