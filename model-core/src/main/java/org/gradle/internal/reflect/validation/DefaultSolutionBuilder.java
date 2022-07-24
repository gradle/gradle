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
package org.gradle.internal.reflect.validation;

import org.gradle.problems.BaseSolution;
import org.gradle.problems.Solution;
import org.gradle.api.internal.DocumentationRegistry;

import java.util.function.Supplier;

public class DefaultSolutionBuilder implements SolutionBuilder {
    private final DocumentationRegistry documentationRegistry;
    private final Supplier<String> shortDescription;
    private Supplier<String> longDescription = () -> null;
    private Supplier<String> documentationLink = () -> null;


    public DefaultSolutionBuilder(DocumentationRegistry documentationRegistry, Supplier<String> shortDescription) {
        this.documentationRegistry = documentationRegistry;
        this.shortDescription = shortDescription;
    }

    @Override
    public SolutionBuilder withLongDescription(Supplier<String> description) {
        this.longDescription = description;
        return this;
    }

    @Override
    public SolutionBuilder withDocumentation(String id, String section) {
        this.documentationLink = () -> documentationRegistry.getDocumentationFor(id, section);
        return this;
    }

    Supplier<Solution> build() {
        return () -> new BaseSolution(shortDescription, longDescription, documentationLink);
    }
}
