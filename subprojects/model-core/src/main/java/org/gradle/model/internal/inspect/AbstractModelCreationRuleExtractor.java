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

import net.jcip.annotations.ThreadSafe;
import org.gradle.api.Nullable;
import org.gradle.model.InvalidModelRuleDeclarationException;
import org.gradle.model.Model;
import org.gradle.model.internal.core.*;

@ThreadSafe
public abstract class AbstractModelCreationRuleExtractor extends AbstractAnnotationDrivenModelRuleExtractor<Model> {

    protected String determineModelName(MethodRuleDefinition<?, ?> ruleDefinition) {
        String annotationValue = ruleDefinition.getAnnotation(Model.class).value();
        String modelName = (annotationValue == null || annotationValue.isEmpty()) ? ruleDefinition.getMethodName() : annotationValue;

        try {
            ModelPath.validatePath(modelName);
        } catch (Exception e) {
            throw new InvalidModelRuleDeclarationException(String.format("Path of declared model element created by rule %s is invalid.", ruleDefinition.getDescriptor()), e);
        }

        return modelName;
    }

    @Nullable
    @Override
    public <R, S> ExtractedModelRule registration(MethodRuleDefinition<R, S> ruleDefinition) {
        String modelName = determineModelName(ruleDefinition);

        ModelPath modelPath = ModelPath.path(modelName);
        ModelRegistrations.Builder registration = ModelRegistrations.of(modelPath).descriptor(ruleDefinition.getDescriptor());

        buildRegistration(ruleDefinition, modelPath, registration);

        registration.hidden(ruleDefinition.isAnnotationPresent(Hidden.class));

        return new ExtractedModelRegistration(registration.build());
    }

    protected abstract <R, S> void buildRegistration(MethodRuleDefinition<R, S> ruleDefinition, ModelPath modelPath, ModelRegistrations.Builder registration);
}
