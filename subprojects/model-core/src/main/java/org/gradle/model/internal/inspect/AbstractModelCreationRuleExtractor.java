/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.model.internal.inspect;

import org.gradle.api.Nullable;
import org.gradle.model.Model;
import org.gradle.model.internal.core.*;

public abstract class AbstractModelCreationRuleExtractor extends AbstractAnnotationDrivenModelRuleExtractor<Model> {

    private ModelPath determineModelName(MethodRuleDefinition<?, ?> ruleDefinition, ValidationProblemCollector problems) {
        String annotationValue = ruleDefinition.getAnnotation(Model.class).value();
        String modelName = (annotationValue == null || annotationValue.isEmpty()) ? ruleDefinition.getMethodName() : annotationValue;

        try {
            ModelPath.validatePath(modelName);
        } catch (Exception e) {
            problems.add(ruleDefinition, "The declared model element path '" + modelName + "' is not a valid path", e);
        }
        return ModelPath.path(modelName);
    }

    @Nullable
    @Override
    public <R, S> ExtractedModelRule registration(MethodRuleDefinition<R, S> ruleDefinition, MethodModelRuleExtractionContext context) {
        ModelPath modelPath = determineModelName(ruleDefinition, context);

        ModelRegistrations.Builder registration = ModelRegistrations.of(modelPath).descriptor(ruleDefinition.getDescriptor());

        buildRegistration(ruleDefinition, modelPath, registration, context);
        if (context.hasProblems()) {
            return null;
        }

        registration.hidden(ruleDefinition.isAnnotationPresent(Hidden.class));

        return new ExtractedModelRegistration(registration.build());
    }

    protected abstract <R, S> void buildRegistration(MethodRuleDefinition<R, S> ruleDefinition, ModelPath modelPath, ModelRegistrations.Builder registration, ValidationProblemCollector problems);
}
