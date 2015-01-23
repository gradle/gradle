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
import org.gradle.language.base.FunctionalSourceSet;
import org.gradle.language.base.plugins.ComponentModelBasePlugin;
import org.gradle.model.InvalidModelRuleDeclarationException;
import org.gradle.model.collection.CollectionBuilder;
import org.gradle.model.internal.core.*;
import org.gradle.model.internal.inspect.MethodRuleDefinition;
import org.gradle.model.internal.registry.ModelRegistry;
import org.gradle.model.internal.type.ModelType;
import org.gradle.platform.base.*;
import org.gradle.platform.base.internal.BinarySpecInternal;
import org.gradle.platform.base.internal.ComponentSpecInternal;

public class ComponentBinariesModelRuleExtractor extends AbstractAnnotationDrivenComponentModelRuleExtractor<ComponentBinaries> {

    @Override
    public <R, S> ModelRuleRegistration registration(MethodRuleDefinition<R, S> ruleDefinition) {
        return createRegistration(ruleDefinition);
    }

    private <R, S extends BinarySpec> ModelRuleRegistration createRegistration(MethodRuleDefinition<R, ?> ruleDefinition) {
        try {
            RuleMethodDataCollector dataCollector = new RuleMethodDataCollector();
            visitAndVerifyMethodSignature(dataCollector, ruleDefinition);

            Class<S> binaryType = dataCollector.getParameterType(BinarySpec.class);
            Class<? extends ComponentSpec> componentType = dataCollector.getParameterType(ComponentSpec.class);
            ModelReference<BinaryContainer> subject = ModelReference.of(ModelPath.path("binaries"), ModelType.of(BinaryContainer.class));
            ComponentBinariesRule<R, S> componentBinariesRule = new ComponentBinariesRule<R, S>(subject, componentType, binaryType, ruleDefinition);

            ImmutableList<ModelType<?>> dependencies = ImmutableList.<ModelType<?>>of(ModelType.of(ComponentModelBasePlugin.class));
            return new ModelMutatorRegistration(ModelActionRole.Mutate, componentBinariesRule, dependencies);
        } catch (InvalidModelException e) {
            throw invalidModelRule(ruleDefinition, e);
        }
    }

    private <S extends BinarySpec, R> void configureMutationRule(ModelRegistry modelRegistry, ModelReference<BinaryContainer> subject, Class<? extends ComponentSpec> componentType, Class<S> binaryType, MethodRuleDefinition<R, ?> ruleDefinition) {
        modelRegistry.apply(ModelActionRole.Mutate, new ComponentBinariesRule<R, S>(subject, componentType, binaryType, ruleDefinition), ModelPath.ROOT);
    }

    private void visitAndVerifyMethodSignature(RuleMethodDataCollector dataCollector, MethodRuleDefinition<?, ?> ruleDefinition) {
        assertIsVoidMethod(ruleDefinition);
        visitCollectionBuilderSubject(dataCollector, ruleDefinition, BinarySpec.class);
        visitDependency(dataCollector, ruleDefinition, ModelType.of(ComponentSpec.class));
    }

    private class ComponentBinariesRule<R, S extends BinarySpec> extends CollectionBuilderBasedRule<R, S, ComponentSpec, BinaryContainer> {

        private final Class<? extends ComponentSpec> componentType;
        private final Class<S> binaryType;

        public ComponentBinariesRule(ModelReference<BinaryContainer> subject, final Class<? extends ComponentSpec> componentType, final Class<S> binaryType, MethodRuleDefinition<R, ?> ruleDefinition) {
            super(subject, componentType, ruleDefinition, ModelReference.of(ComponentSpecContainer.class));
            this.componentType = componentType;
            this.binaryType = binaryType;
        }

        public void execute(MutableModelNode modelNode, BinaryContainer binaries, Inputs inputs) {
            ComponentSpecContainer componentSpecs = inputs.get(0, ModelType.of(ComponentSpecContainer.class)).getInstance();

            for (final ComponentSpec componentSpec : componentSpecs.withType(componentType)) {
                NamedEntityInstantiator<S> namedEntityInstantiator = new Instantiator<S>(componentSpec, binaries);
                CollectionBuilder<S> collectionBuilder = new DefaultCollectionBuilder<S>(
                        ModelType.of(binaryType),
                        namedEntityInstantiator,
                        binaries,
                        getDescriptor(),
                        modelNode
                );
                invoke(inputs, collectionBuilder, componentSpec, componentSpecs);
            }
        }
    }


    protected InvalidModelRuleDeclarationException invalidModelRule(MethodRuleDefinition<?, ?> ruleDefinition, InvalidModelException e) {
        StringBuilder sb = new StringBuilder();
        ruleDefinition.getDescriptor().describeTo(sb);
        sb.append(" is not a valid ComponentBinaries model rule method.");
        return new InvalidModelRuleDeclarationException(sb.toString(), e);
    }

    private class Instantiator<S extends BinarySpec> implements NamedEntityInstantiator<S> {
        private final ComponentSpec componentSpec;
        private final BinaryContainer container;

        public Instantiator(ComponentSpec componentSpec, BinaryContainer container) {
            this.componentSpec = componentSpec;
            this.container = container;
        }

        public <U extends S> U create(String name, Class<U> type) {
            U binary = container.create(name, type);
            bindBinaryToComponent(componentSpec, binary, name);
            return binary;
        }

        private <U extends S> void bindBinaryToComponent(ComponentSpec componentSpec, U binary, String name) {
            componentSpec.getBinaries().add(binary);
            BinarySpecInternal binaryInternal = (BinarySpecInternal) binary;
            FunctionalSourceSet binarySourceSet = ((ComponentSpecInternal) componentSpec).getSources().copy(name);
            binaryInternal.setBinarySources(binarySourceSet);
        }
    }
}
