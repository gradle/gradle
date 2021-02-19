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

package org.gradle.internal.reflect;

import org.gradle.api.Action;
import org.gradle.api.internal.DocumentationRegistry;
import org.gradle.internal.reflect.validation.DefaultPropertyValidationProblemBuilder;
import org.gradle.internal.reflect.validation.DefaultTypeValidationProblemBuilder;
import org.gradle.internal.reflect.validation.PropertyProblemBuilder;
import org.gradle.internal.reflect.validation.Severity;
import org.gradle.internal.reflect.validation.TypeProblemBuilder;
import org.gradle.internal.reflect.validation.TypeValidationContext;
import org.gradle.internal.reflect.validation.TypeValidationProblem;

import javax.annotation.Nullable;

abstract public class MessageFormattingTypeValidationContext implements TypeValidationContext {
    private final DocumentationRegistry documentationRegistry;
    private final Class<?> rootType;

    public MessageFormattingTypeValidationContext(DocumentationRegistry documentationRegistry,
                                                  @Nullable Class<?> rootType) {
        this.documentationRegistry = documentationRegistry;
        this.rootType = rootType;
    }

    @Override
    public void visitTypeProblem(Action<? super TypeProblemBuilder> problemSpec) {
        DefaultTypeValidationProblemBuilder builder = new DefaultTypeValidationProblemBuilder(documentationRegistry);
        problemSpec.execute(builder);
        recordProblem(builder.build());
    }

    @Override
    public void visitPropertyProblem(Action<? super PropertyProblemBuilder> problemSpec) {
        DefaultPropertyValidationProblemBuilder builder = new DefaultPropertyValidationProblemBuilder(documentationRegistry);
        problemSpec.execute(builder);
        builder.forType(rootType);
        recordProblem(builder.build());
    }


    @Override
    public void visitTypeProblem(Severity kind, Class<?> type, String message) {
        visitTypeProblem(problem -> problem.reportAs(kind)
            .forType(type)
            .withDescription(message));
    }

    @Override
    public void visitPropertyProblem(Severity kind, @Nullable String parentProperty, @Nullable String property, String message) {
        visitPropertyProblem(problem -> {
            PropertyProblemBuilder problemBuilder = problem.reportAs(kind.toReportableSeverity());
            // this code should go away once all messages go through the builder instead
            if (kind == Severity.CACHEABILITY_WARNING) {
                problemBuilder.onlyAffectsCacheableWork();
            }
            problemBuilder.forProperty(parentProperty, property)
                .withDescription(message);
        });
    }

    abstract protected void recordProblem(TypeValidationProblem problem);
}
