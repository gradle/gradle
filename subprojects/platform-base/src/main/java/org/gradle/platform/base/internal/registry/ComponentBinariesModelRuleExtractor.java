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

package org.gradle.platform.base.internal.registry;

import com.google.common.collect.ImmutableList;
import org.gradle.internal.TriAction;
import org.gradle.language.base.plugins.ComponentModelBasePlugin;
import org.gradle.model.InvalidModelRuleDeclarationException;
import org.gradle.model.internal.core.*;
import org.gradle.model.internal.inspect.MethodRuleDefinition;
import org.gradle.model.internal.type.ModelType;
import org.gradle.platform.base.*;

import java.util.List;

public class ComponentBinariesModelRuleExtractor extends AbstractAnnotationDrivenComponentModelRuleExtractor<ComponentBinaries> {

    @Override
    public <R, S> ExtractedModelRule registration(MethodRuleDefinition<R, S> ruleDefinition) {
        return createRegistration(ruleDefinition);
    }

    private <R, S extends BinarySpec, C extends ComponentSpec> ExtractedModelRule createRegistration(MethodRuleDefinition<R, ?> ruleDefinition) {
        try {
            RuleMethodDataCollector dataCollector = new RuleMethodDataCollector();
            visitAndVerifyMethodSignature(dataCollector, ruleDefinition);

            Class<S> binaryType = dataCollector.getParameterType(BinarySpec.class);
            Class<C> componentType = dataCollector.getParameterType(ComponentSpec.class);
            ModelReference<ComponentSpecContainer> subject = ModelReference.of(ModelPath.path("components"), ModelType.of(ComponentSpecContainer.class));
            ComponentBinariesRule<R, S, C> componentBinariesRule = new ComponentBinariesRule<R, S, C>(subject, componentType, binaryType, ruleDefinition);

            return new ExtractedModelAction(ModelActionRole.Finalize, ImmutableList.of(ComponentModelBasePlugin.class), componentBinariesRule);
        } catch (InvalidModelException e) {
            throw invalidModelRule(ruleDefinition, e);
        }
    }

    private void visitAndVerifyMethodSignature(RuleMethodDataCollector dataCollector, MethodRuleDefinition<?, ?> ruleDefinition) {
        assertIsVoidMethod(ruleDefinition);
        visitSubject(dataCollector, ruleDefinition, BinarySpec.class);
        visitDependency(dataCollector, ruleDefinition, ModelType.of(ComponentSpec.class));
    }

    private class ComponentBinariesRule<R, S extends BinarySpec, C extends ComponentSpec> extends ModelMapBasedRule<R, S, ComponentSpec, ComponentSpecContainer> {

        private final Class<C> componentType;
        private final Class<S> binaryType;

        public ComponentBinariesRule(ModelReference<ComponentSpecContainer> subject, final Class<C> componentType, final Class<S> binaryType, MethodRuleDefinition<R, ?> ruleDefinition) {
            super(subject, componentType, ruleDefinition);
            this.componentType = componentType;
            this.binaryType = binaryType;
        }

        public void execute(final MutableModelNode modelNode, final ComponentSpecContainer componentSpecs, final List<ModelView<?>> modelMapRuleInputs) {
            modelNode.applyToAllLinks(ModelActionRole.Mutate, DirectNodeInputUsingModelAction.of(
                ModelReference.of(ModelType.of(componentType)),
                getDescriptor(),
                getInputs(),
                new TriAction<MutableModelNode, C, List<ModelView<?>>>() {
                    @Override
                    public void execute(MutableModelNode componentModelNode, C component, final List<ModelView<?>> componentRuleInputs) {
                        invoke(componentRuleInputs, component.getBinaries().withType(binaryType), component);
                    }
                }
            ));
        }
    }

    protected InvalidModelRuleDeclarationException invalidModelRule(MethodRuleDefinition<?, ?> ruleDefinition, InvalidModelException e) {
        StringBuilder sb = new StringBuilder();
        ruleDefinition.getDescriptor().describeTo(sb);
        sb.append(" is not a valid ComponentBinaries model rule method.");
        return new InvalidModelRuleDeclarationException(sb.toString(), e);
    }

}
