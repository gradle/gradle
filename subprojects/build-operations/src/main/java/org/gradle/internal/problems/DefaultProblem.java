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

package org.gradle.internal.problems;

import org.gradle.api.NonNullApi;
import org.gradle.api.problems.interfaces.ProblemId;
import org.gradle.api.problems.interfaces.ProblemLocation;
import org.gradle.api.problems.interfaces.Severity;
import org.gradle.api.problems.interfaces.Solution;
import org.gradle.api.problems.interfaces.Problem;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;

@NonNullApi
public class DefaultProblem implements Problem {

    private final ProblemId id;
    private final String message;
    private final Severity severity;
    private final ProblemLocation where;
    private final String why;
    private final String documentationLink;
    private final String description;
    private final List<Solution> solutions;

    public DefaultProblem(ProblemId id, String message, Severity severity, ProblemLocation where, String why, String documentationLink, String description, List<Solution> solutions) {
        this.id = id;
        this.message = message;
        this.severity = severity;
        this.where = where;
        this.why = why;
        this.documentationLink = documentationLink;
        this.description = description;
        this.solutions = solutions == null ? Collections.<Solution>emptyList() : solutions;
    }

    @Override
    public ProblemId getId() {
        return id;
    }

    @Override
    public String getMessage() {
        return message;
    }

    @Override
    public Severity getSeverity() {
        return severity;
    }

    @Override
    @Nullable
    public ProblemLocation getWhere() {
        return where;
    }

    @Nullable
    @Override
    public String getWhy() {
        return why;
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
    public List<Solution> getSolutions() {
        return solutions;
    }
}
