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
import org.gradle.internal.Cast;
import org.gradle.model.InvalidModelRuleDeclarationException;
import org.gradle.model.Model;
import org.gradle.model.internal.core.Hidden;
import org.gradle.model.internal.core.ModelPath;
import org.gradle.model.internal.core.ModelRegistrations;
import org.gradle.model.internal.core.MutableModelNode;

import java.util.Collections;
import java.util.List;

public abstract class AbstractModelCreationRuleExtractor extends AbstractAnnotationDrivenModelRuleExtractor<Model> {

    private ModelPath determineModelName(MethodRuleDefinition<?, ?> ruleDefinition, RuleSourceValidationProblemCollector problems) {
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

        validateMethod(ruleDefinition, context);
        if (context.hasProblems()) {
            return null;
        }

        return buildRule(modelPath, ruleDefinition);
    }

    protected <R, S> void validateMethod(MethodRuleDefinition<R, S> ruleDefinition, MethodModelRuleExtractionContext context) {
    }

    protected abstract <R, S> ExtractedModelRule buildRule(ModelPath modelPath, MethodRuleDefinition<R, S> ruleDefinition);

    protected static abstract class ExtractedCreationRule<R, S>  extends AbstractExtractedModelRule {
        protected final ModelPath modelPath;
        private final boolean hidden;

        public ExtractedCreationRule(ModelPath modelPath, MethodRuleDefinition<R, S> ruleDefinition) {
            super(ruleDefinition);
            this.modelPath = modelPath;
            this.hidden = ruleDefinition.isAnnotationPresent(Hidden.class);
        }

        protected abstract void buildRegistration(MethodModelRuleApplicationContext context, ModelRegistrations.Builder registration);

        @Override
        public void apply(MethodModelRuleApplicationContext context, MutableModelNode target) {
            if (!target.getPath().equals(ModelPath.ROOT)) {
                throw new InvalidModelRuleDeclarationException(String.format("Rule %s cannot be applied at the scope of model element %s as creation rules cannot be used when applying rule sources to particular elements", getRuleDefinition().getDescriptor(), target.getPath()));
            }
            ModelRegistrations.Builder registration = ModelRegistrations.of(modelPath).descriptor(getRuleDefinition().getDescriptor());
            buildRegistration(context, registration);
            registration.hidden(hidden);

            context.getRegistry().register(registration.build());
        }

        @Override
        public List<Class<?>> getRuleDependencies() {
            return Collections.emptyList();
        }

        @Override
        public MethodRuleDefinition<R, S> getRuleDefinition() {
            return Cast.uncheckedCast(super.getRuleDefinition());
        }
    }
}
