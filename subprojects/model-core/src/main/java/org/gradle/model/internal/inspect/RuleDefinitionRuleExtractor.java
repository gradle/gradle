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

import org.gradle.internal.BiAction;
import org.gradle.internal.reflect.DirectInstantiator;
import org.gradle.model.RuleSource;
import org.gradle.model.Rules;
import org.gradle.model.internal.core.*;
import org.gradle.model.internal.registry.ModelRegistry;
import org.gradle.model.internal.type.ModelType;

import java.util.Collections;
import java.util.List;

public class RuleDefinitionRuleExtractor extends AbstractAnnotationDrivenModelRuleExtractor<Rules> {
    private static final ModelType<RuleSource> RULE_SOURCE_MODEL_TYPE = ModelType.of(RuleSource.class);

    @Override
    public <R, S> ExtractedModelRule registration(final MethodRuleDefinition<R, S> ruleDefinition, ValidationProblemCollector problems) {
        ModelType<?> subjectType = ruleDefinition.getReferences().get(0).getType();
        if (!RULE_SOURCE_MODEL_TYPE.isAssignableFrom(subjectType)) {
            problems.add(ruleDefinition, "first parameter must be a RuleSource subtype");
            return null;
        }

        final ModelType<? extends RuleSource> ruleSourceType = subjectType.asSubtype(RULE_SOURCE_MODEL_TYPE);
        return new ExtractedModelRule() {
            @Override
            public void apply(ModelRegistry modelRegistry, ModelPath scope) {
                modelRegistry.configure(ModelActionRole.Initialize,
                        DirectNodeInputUsingModelAction.of(ModelReference.of(scope), ruleDefinition.getDescriptor(), ruleDefinition.getTailReferences(), new BiAction<MutableModelNode, List<ModelView<?>>>() {
                            @Override
                            public void execute(MutableModelNode subjectNode, List<ModelView<?>> modelViews) {
                                Object[] parameters = new Object[1 + modelViews.size()];
                                parameters[0] = DirectInstantiator.INSTANCE.newInstance(ruleSourceType.getConcreteClass());
                                for (int i = 1; i < parameters.length; i++) {
                                    parameters[i] = modelViews.get(i + 1).getInstance();
                                }
                                ruleDefinition.getRuleInvoker().invoke(parameters);
                                subjectNode.applyToSelf(ruleSourceType.getConcreteClass());
                            }
                        }));
            }

            @Override
            public List<? extends Class<?>> getRuleDependencies() {
                return Collections.emptyList();
            }
        };
    }
}
