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
import org.gradle.internal.BiAction;
import org.gradle.language.base.FunctionalSourceSet;
import org.gradle.language.base.plugins.ComponentModelBasePlugin;
import org.gradle.model.InvalidModelRuleDeclarationException;
import org.gradle.model.collection.CollectionBuilder;
import org.gradle.model.internal.core.*;
import org.gradle.model.internal.core.DelegatingCollectionBuilder;
import org.gradle.model.internal.inspect.MethodRuleDefinition;
import org.gradle.model.internal.type.ModelType;
import org.gradle.platform.base.*;
import org.gradle.platform.base.internal.BinarySpecInternal;
import org.gradle.platform.base.internal.ComponentSpecInternal;

import java.util.List;

public class ComponentBinariesModelRuleExtractor extends AbstractAnnotationDrivenComponentModelRuleExtractor<ComponentBinaries> {

    @Override
    public <R, S> ExtractedModelRule registration(MethodRuleDefinition<R, S> ruleDefinition) {
        return createRegistration(ruleDefinition);
    }

    private <R, S extends BinarySpec> ExtractedModelRule createRegistration(MethodRuleDefinition<R, ?> ruleDefinition) {
        try {
            RuleMethodDataCollector dataCollector = new RuleMethodDataCollector();
            visitAndVerifyMethodSignature(dataCollector, ruleDefinition);

            Class<S> binaryType = dataCollector.getParameterType(BinarySpec.class);
            Class<? extends ComponentSpec> componentType = dataCollector.getParameterType(ComponentSpec.class);
            ModelReference<CollectionBuilder<BinarySpec>> subject = ModelReference.of(ModelPath.path("binaries"), DefaultCollectionBuilder.typeOf(ModelType.of(BinarySpec.class)));
            ComponentBinariesRule<R, S> componentBinariesRule = new ComponentBinariesRule<R, S>(subject, componentType, binaryType, ruleDefinition);

            return new ExtractedModelAction(ModelActionRole.Mutate, ImmutableList.of(ComponentModelBasePlugin.class), componentBinariesRule);
        } catch (InvalidModelException e) {
            throw invalidModelRule(ruleDefinition, e);
        }
    }

    private void visitAndVerifyMethodSignature(RuleMethodDataCollector dataCollector, MethodRuleDefinition<?, ?> ruleDefinition) {
        assertIsVoidMethod(ruleDefinition);
        visitCollectionBuilderSubject(dataCollector, ruleDefinition, BinarySpec.class);
        visitDependency(dataCollector, ruleDefinition, ModelType.of(ComponentSpec.class));
    }

    private class ComponentBinariesRule<R, S extends BinarySpec> extends CollectionBuilderBasedRule<R, S, ComponentSpec, CollectionBuilder<BinarySpec>> {

        private final Class<? extends ComponentSpec> componentType;
        private final Class<S> binaryType;

        public ComponentBinariesRule(ModelReference<CollectionBuilder<BinarySpec>> subject, final Class<? extends ComponentSpec> componentType, final Class<S> binaryType, MethodRuleDefinition<R, ?> ruleDefinition) {
            super(subject, componentType, ruleDefinition, ModelReference.of(ComponentSpecContainer.class));
            this.componentType = componentType;
            this.binaryType = binaryType;
        }

        public void execute(MutableModelNode modelNode, final CollectionBuilder<BinarySpec> binaries, List<ModelView<?>> inputs) {
            ComponentSpecContainer componentSpecs = ModelViews.assertType(inputs.get(0), ModelType.of(ComponentSpecContainer.class)).getInstance();

            for (final ComponentSpec componentSpec : componentSpecs.withType(componentType)) {
                CollectionBuilder<S> typed = binaries.withType(binaryType);
                CollectionBuilder<S> wrapped = new DelegatingCollectionBuilder<S>(typed, ModelType.of(binaryType), new BiAction<String, ModelType<? extends S>>() {
                    @Override
                    public void execute(String s, ModelType<? extends S> modelType) {
                        BinarySpec binary = binaries.get(s);
                        assert binary != null : "binary should not be null";
                        componentSpec.getBinaries().add(binary);
                        BinarySpecInternal binaryInternal = (BinarySpecInternal) binary;
                        FunctionalSourceSet binarySourceSet = ((ComponentSpecInternal) componentSpec).getSources().copy(s);
                        binaryInternal.setBinarySources(binarySourceSet);
                    }
                });

                invoke(inputs, wrapped, componentSpec, componentSpecs);
            }
        }
    }


    protected InvalidModelRuleDeclarationException invalidModelRule(MethodRuleDefinition<?, ?> ruleDefinition, InvalidModelException e) {
        StringBuilder sb = new StringBuilder();
        ruleDefinition.getDescriptor().describeTo(sb);
        sb.append(" is not a valid ComponentBinaries model rule method.");
        return new InvalidModelRuleDeclarationException(sb.toString(), e);
    }

}
