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
import org.gradle.model.RuleSource;
import org.gradle.model.Rules;
import org.gradle.model.internal.core.ModelActionRole;
import org.gradle.model.internal.core.ModelReference;
import org.gradle.model.internal.core.ModelView;
import org.gradle.model.internal.core.MutableModelNode;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;
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
        RuleApplicationScope ruleApplicationScope = RuleApplicationScope.fromRuleDefinition(context, ruleDefinition, 1);
        return new ExtractedRuleSourceDefinitionRule(ruleDefinition, ruleSourceType, context.getRuleExtractor(), ruleApplicationScope);
    }

    private static class ExtractedRuleSourceDefinitionRule  extends AbstractExtractedModelRule {
        private final ModelType<? extends RuleSource> ruleSourceType;
        private final ModelRuleExtractor ruleExtractor;
        private final RuleApplicationScope ruleApplicationScope;

        public ExtractedRuleSourceDefinitionRule(MethodRuleDefinition<?, ?> ruleDefinition, ModelType<? extends RuleSource> ruleSourceType, ModelRuleExtractor ruleExtractor, RuleApplicationScope ruleApplicationScope) {
            super(ruleDefinition);
            this.ruleSourceType = ruleSourceType;
            this.ruleExtractor = ruleExtractor;
            this.ruleApplicationScope = ruleApplicationScope;
        }

        @Override
        public void apply(final MethodModelRuleApplicationContext context, MutableModelNode target) {
            MethodRuleDefinition<?, ?> ruleDefinition = getRuleDefinition();
            ModelReference<?> targetReference = ruleDefinition.getReferences().get(1);
            List<ModelReference<?>> inputs = ruleDefinition.getReferences().subList(2, ruleDefinition.getReferences().size());
            RuleSourceApplicationAction ruleAction = new RuleSourceApplicationAction(targetReference, ruleDefinition.getDescriptor(), inputs, ruleSourceType, ruleExtractor);
            RuleExtractorUtils.configureRuleAction(context, ruleApplicationScope, ModelActionRole.Defaults, ruleAction);
        }

        @Override
        public List<? extends Class<?>> getRuleDependencies() {
            return Collections.emptyList();
        }
    }

    private static class RuleSourceApplicationAction implements MethodRuleAction {
        private final ModelReference<?> targetReference;
        private final ModelRuleDescriptor descriptor;
        private final List<ModelReference<?>> inputs;
        private final ModelType<? extends RuleSource> ruleSourceType;
        private final ModelRuleExtractor ruleExtractor;

        public RuleSourceApplicationAction(ModelReference<?> targetReference, ModelRuleDescriptor descriptor, List<ModelReference<?>> inputs, ModelType<? extends RuleSource> ruleSourceType, ModelRuleExtractor ruleExtractor) {
            this.targetReference = targetReference;
            this.descriptor = descriptor;
            this.inputs = inputs;
            this.ruleSourceType = ruleSourceType;
            this.ruleExtractor = ruleExtractor;
        }

        @Override
        public ModelReference<?> getSubject() {
            return targetReference;
        }

        @Override
        public List<? extends ModelReference<?>> getInputs() {
            return inputs;
        }

        @Override
        public void execute(ModelRuleInvoker<?> invoker, MutableModelNode subjectNode, List<ModelView<?>> inputs) {
            ExtractedRuleSource<?> ruleSource = ruleExtractor.extract(ruleSourceType.getConcreteClass());
            Object[] parameters = new Object[2 + inputs.size()];
            parameters[0] = ruleSource.getFactory().create();
            parameters[1] = subjectNode.asImmutable(targetReference.getType(), descriptor).getInstance();
            for (int i = 0; i < inputs.size(); i++) {
                parameters[i + 2] = inputs.get(i).getInstance();
            }
            invoker.invoke(parameters);
            subjectNode.applyToSelf(ruleSource);
        }
    }
}
