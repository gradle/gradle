/*
 * Copyright 2015 the original author or authors.
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
import org.gradle.internal.BiAction;
import org.gradle.model.RuleSource;
import org.gradle.model.Rules;
import org.gradle.model.internal.core.*;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;
import org.gradle.model.internal.registry.ModelRegistry;
import org.gradle.model.internal.type.ModelType;

import java.util.Collections;
import java.util.List;

public class RuleDefinitionRuleExtractor extends AbstractAnnotationDrivenModelRuleExtractor<Rules> {
    private static final ModelType<RuleSource> RULE_SOURCE_MODEL_TYPE = ModelType.of(RuleSource.class);

    @Nullable
    @Override
    public <R, S> ExtractedModelRule registration(MethodRuleDefinition<R, S> ruleDefinition, MethodModelRuleExtractionContext context) {
        validateIsVoidMethod(ruleDefinition, context);
        if (ruleDefinition.getReferences().size() < 2) {
            context.add(ruleDefinition, "A method " + getDescription() + " must have at least two parameters");
            return null;
        }

        ModelType<?> ruleType = ruleDefinition.getReferences().get(0).getType();
        if (!RULE_SOURCE_MODEL_TYPE.isAssignableFrom(ruleType)) {
            context.add(ruleDefinition, "The first parameter of a method " + getDescription() + " must be a subtype of " + RuleSource.class.getName());
        }
        if (context.hasProblems()) {
            return null;
        }

        ModelType<? extends RuleSource> ruleSourceType = ruleType.asSubtype(RULE_SOURCE_MODEL_TYPE);
        return new RuleSourceDefinitionAction(ruleDefinition, ruleSourceType, context.getRuleExtractor());
    }

    private static class RuleSourceDefinitionAction implements ExtractedModelRule {
        private final MethodRuleDefinition<?, ?> ruleDefinition;
        private final ModelType<? extends RuleSource> ruleSourceType;
        private final ModelRuleExtractor ruleExtractor;

        public RuleSourceDefinitionAction(MethodRuleDefinition<?, ?> ruleDefinition, ModelType<? extends RuleSource> ruleSourceType, ModelRuleExtractor ruleExtractor) {
            this.ruleDefinition = ruleDefinition;
            this.ruleSourceType = ruleSourceType;
            this.ruleExtractor = ruleExtractor;
        }

        @Override
        public ModelRuleDescriptor getDescriptor() {
            return ruleDefinition.getDescriptor();
        }

        @Override
        public void apply(ModelRegistry modelRegistry, ModelPath scope) {
            final ModelReference<?> targetReference = ruleDefinition.getReferences().get(1);
            List<ModelReference<?>> inputs = ruleDefinition.getReferences().subList(2, ruleDefinition.getReferences().size());

            modelRegistry.configure(ModelActionRole.Defaults,
                    DirectNodeInputUsingModelAction.of(targetReference, ruleDefinition.getDescriptor(), inputs, new BiAction<MutableModelNode, List<ModelView<?>>>() {
                        @Override
                        public void execute(MutableModelNode subjectNode, List<ModelView<?>> modelViews) {
                            ExtractedRuleSource<?> ruleSource = ruleExtractor.extract(ruleSourceType.getConcreteClass());
                            Object[] parameters = new Object[2 + modelViews.size()];
                            parameters[0] = ruleSource.getFactory().create();
                            parameters[1] = subjectNode.asImmutable(targetReference.getType(), ruleDefinition.getDescriptor()).getInstance();
                            for (int i = 2; i < parameters.length; i++) {
                                parameters[i] = modelViews.get(i).getInstance();
                            }
                            ruleDefinition.getRuleInvoker().invoke(parameters);
                            subjectNode.applyToSelf(ruleSource);
                        }
                    }));
        }

        @Override
        public List<? extends Class<?>> getRuleDependencies() {
            return Collections.emptyList();
        }
    }
}
