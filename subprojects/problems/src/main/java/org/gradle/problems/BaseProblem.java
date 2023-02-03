/*
 * Copyright 2021 the original author or authors.
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
package org.gradle.problems;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class BaseProblem<ID extends Enum<ID>, SEVERITY extends Enum<SEVERITY>, CONTEXT> implements Problem<ID, SEVERITY, CONTEXT> {
    private final ID id;
    private final SEVERITY severity;
    private final CONTEXT context;
    private final Supplier<String> shortDescription;
    private final Supplier<String> longDescription;
    private final Supplier<String> reason;
    private final Supplier<String> docUrl;
    private final List<Supplier<Solution>> solutions;

    public BaseProblem(ID id,
                          SEVERITY severity,
                          CONTEXT context,
                          Supplier<String> shortDescription,
                          Supplier<String> longDescription,
                          Supplier<String> reason,
                          Supplier<String> docUrl,
                          List<Supplier<Solution>> solutions) {
        this.id = id;
        this.severity = severity;
        this.context = Objects.requireNonNull(context, "context must not be null");
        this.shortDescription = Objects.requireNonNull(shortDescription, "short description supplier must not be null");
        this.longDescription = Objects.requireNonNull(longDescription, "long description supplier must not be null");
        this.reason = Objects.requireNonNull(reason, "reason supplier must not be null");
        this.docUrl = Objects.requireNonNull(docUrl, "documentation link supplier must not be null");
        this.solutions = Objects.requireNonNull(solutions, "solutions must not be null");
    }

    @Override
    public SEVERITY getSeverity() {
        return severity;
    }

    @Override
    public CONTEXT getWhere() {
        return context;
    }

    @Override
    public Optional<String> getWhy() {
        return Optional.ofNullable(reason.get());
    }

    @Override
    public Optional<String> getDocumentationLink() {
        return Optional.ofNullable(docUrl.get());
    }

    @Override
    public ID getId() {
        return id;
    }

    @Override
    public String getShortDescription() {
        return shortDescription.get();
    }

    @Override
    public Optional<String> getLongDescription() {
        return Optional.ofNullable(longDescription.get());
    }

    @Override
    public List<Solution> getPossibleSolutions() {
        return solutions.stream().map(Supplier::get).collect(Collectors.toList());
    }
}
