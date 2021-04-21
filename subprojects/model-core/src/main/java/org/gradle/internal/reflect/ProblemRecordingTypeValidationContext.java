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
import org.gradle.internal.reflect.validation.TypeProblemBuilder;
import org.gradle.internal.reflect.validation.TypeValidationContext;
import org.gradle.internal.reflect.validation.TypeValidationProblem;
import org.gradle.plugin.use.PluginId;

import javax.annotation.Nullable;

abstract public class ProblemRecordingTypeValidationContext implements TypeValidationContext {
    private final DocumentationRegistry documentationRegistry;
    private final Class<?> rootType;
    private final PluginId pluginId;

    public ProblemRecordingTypeValidationContext(DocumentationRegistry documentationRegistry,
                                                 @Nullable Class<?> rootType,
                                                 @Nullable PluginId pluginId) {
        this.documentationRegistry = documentationRegistry;
        this.rootType = rootType;
        this.pluginId = pluginId;
    }

    @Override
    public void visitTypeProblem(Action<? super TypeProblemBuilder> problemSpec) {
        DefaultTypeValidationProblemBuilder builder = new DefaultTypeValidationProblemBuilder(documentationRegistry, pluginId);
        problemSpec.execute(builder);
        recordProblem(builder.build());
    }

    @Override
    public void visitPropertyProblem(Action<? super PropertyProblemBuilder> problemSpec) {
        DefaultPropertyValidationProblemBuilder builder = new DefaultPropertyValidationProblemBuilder(documentationRegistry, pluginId);
        problemSpec.execute(builder);
        builder.forType(rootType);
        recordProblem(builder.build());
    }

    abstract protected void recordProblem(TypeValidationProblem problem);
}
