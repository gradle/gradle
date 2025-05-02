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

import org.gradle.tooling.events.problems.DocumentationLink;
import org.gradle.tooling.events.problems.ProblemDefinition;
import org.gradle.tooling.events.problems.ProblemId;
import org.gradle.tooling.events.problems.Severity;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public class DefaultProblemDefinition implements ProblemDefinition {

    private final ProblemId id;
    private final Severity severity;
    @Nullable
    private final DocumentationLink documentationLink;

    public DefaultProblemDefinition(ProblemId id, Severity severity, DocumentationLink documentationLink) {
        this.id = id;
        this.severity = severity;
        this.documentationLink = documentationLink;
    }

    @Override
    public ProblemId getId() {
        return id;
    }

    @Override
    public Severity getSeverity() {
        return severity;
    }

    @Override
    @Nullable
    public DocumentationLink getDocumentationLink() {
        return documentationLink;
    }
}
