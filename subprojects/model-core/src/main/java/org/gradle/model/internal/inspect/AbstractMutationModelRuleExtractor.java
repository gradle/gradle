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
import org.gradle.model.internal.core.ModelActionRole;
import org.gradle.model.internal.core.MutableModelNode;

import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.List;

public abstract class AbstractMutationModelRuleExtractor<T extends Annotation> extends AbstractAnnotationDrivenModelRuleExtractor<T> {
    @Nullable
    @Override
    public <R, S> ExtractedModelRule registration(final MethodRuleDefinition<R, S> ruleDefinition, MethodModelRuleExtractionContext context) {
        validateIsVoidMethod(ruleDefinition, context);
        if (ruleDefinition.getReferences().isEmpty()) {
            context.add(ruleDefinition, "A method " + getDescription() + " must have at least one parameter");
        }
        if (context.hasProblems()) {
            return null;
        }
        RuleApplicationScope ruleApplicationScope = RuleApplicationScope.fromRuleDefinition(context, ruleDefinition, 0);
        return new ExtractedMutationRule<S>(getMutationType(), ruleDefinition, ruleApplicationScope);
    }

    protected abstract ModelActionRole getMutationType();

    private static class ExtractedMutationRule<S>  extends AbstractExtractedModelRule {
        private final ModelActionRole mutationType;
        private final RuleApplicationScope ruleApplicationScope;

        public ExtractedMutationRule(ModelActionRole mutationType, MethodRuleDefinition<?, S> ruleDefinition, RuleApplicationScope ruleApplicationScope) {
            super(ruleDefinition);
            this.mutationType = mutationType;
            this.ruleApplicationScope = ruleApplicationScope;
        }

        @Override
        public void apply(MethodModelRuleApplicationContext context, MutableModelNode target) {
            MethodRuleDefinition<?, S> ruleDefinition = Cast.uncheckedCast(getRuleDefinition());
            MethodBackedModelAction<S> ruleAction = new MethodBackedModelAction<S>(ruleDefinition.getDescriptor(), ruleDefinition.getSubjectReference(), ruleDefinition.getTailReferences());
            RuleExtractorUtils.configureRuleAction(context, ruleApplicationScope, mutationType, ruleAction);
        }

        @Override
        public List<? extends Class<?>> getRuleDependencies() {
            return Collections.emptyList();
        }
    }
}
