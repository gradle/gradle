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
import org.gradle.api.Action;
import org.gradle.language.base.plugins.ComponentModelBasePlugin;
import org.gradle.model.InvalidModelRuleDeclarationException;
import org.gradle.model.collection.CollectionBuilder;
import org.gradle.model.internal.core.*;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;
import org.gradle.model.internal.inspect.MethodRuleDefinition;
import org.gradle.model.internal.inspect.RuleSourceDependencies;
import org.gradle.model.internal.registry.ModelRegistry;
import org.gradle.platform.base.*;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class ComponentBinariesRuleDefinitionHandler extends AbstractAnnotationDrivenMethodComponentRuleDefinitionHandler<ComponentBinaries> {

    public <R> void register(final MethodRuleDefinition<R> ruleDefinition, final ModelRegistry modelRegistry, RuleSourceDependencies dependencies) {
        doRegister(ruleDefinition, modelRegistry, dependencies);
    }

    private <R, S extends BinarySpec> void doRegister(MethodRuleDefinition<R> ruleDefinition, ModelRegistry modelRegistry, RuleSourceDependencies dependencies) {
        try {
            RuleMethodDataCollector dataCollector = new RuleMethodDataCollector();
            visitAndVerifyMethodSignature(dataCollector, ruleDefinition);

            final Class<S> binaryType = dataCollector.getParameterType(BinarySpec.class);
            final Class<? extends ComponentSpec<S>> componentType = dataCollector.getParameterType(ComponentSpec.class);

            validateComponentType(binaryType, componentType);

            dependencies.add(ComponentModelBasePlugin.class);
            final ModelReference<CollectionBuilder<S>> subject = ModelReference.of(ModelPath.path("binaries"), new ModelType<CollectionBuilder<S>>() {
            });

            configureMutationRule(modelRegistry, subject, componentType, binaryType, ruleDefinition);
        } catch (InvalidComponentModelException e) {
            invalidModelRule(ruleDefinition, e);
        }
    }

    private <S extends BinarySpec, R> void configureMutationRule(ModelRegistry modelRegistry, ModelReference<CollectionBuilder<S>> subject, Class<? extends ComponentSpec<S>> componentType, Class<S> binaryType, MethodRuleDefinition<R> ruleDefinition) {
        modelRegistry.mutate(new ComponentBinariesRule<R, S>(subject, componentType, binaryType, ruleDefinition, modelRegistry));
    }

    private <R> void visitAndVerifyMethodSignature(RuleMethodDataCollector dataCollector, MethodRuleDefinition<R> ruleDefinition) {
        assertIsVoidMethod(ruleDefinition);
        visitCollectionBuilderSubject(dataCollector, ruleDefinition, BinarySpec.class);
        visitDependency(dataCollector, ruleDefinition, ModelType.of(ComponentSpec.class));
    }

    private <T extends BinarySpec> void validateComponentType(Class<T> expectedBinaryType, Class<? extends ComponentSpec<T>> componentType) {
        if (componentType == null) {
            throw new InvalidComponentModelException(String.format("%s method must have one parameter extending %s. Found no parameter extending %s.", annotationType.getSimpleName(),
                    ComponentSpec.class.getSimpleName(),
                    ComponentSpec.class.getSimpleName()));
        }

        for (Type type : componentType.getGenericInterfaces()) {
            if (type instanceof ParameterizedType) {
                ParameterizedType parameterizedType = (ParameterizedType) type;
                if (parameterizedType.getRawType().equals(ComponentSpec.class)) {
                    for (Type givenBinaryType : parameterizedType.getActualTypeArguments()) {
                        if (((Class<?>) givenBinaryType).isAssignableFrom(expectedBinaryType)) {
                            return;
                        }
                    }
                }
            }
        }
        throw new InvalidComponentModelException(String.format("%s method parameter of type %s does not support binaries of type %s.", annotationType.getSimpleName(),
                componentType.getSimpleName(),
                expectedBinaryType.getSimpleName()));

    }

    private class ComponentBinariesRule<R, S extends BinarySpec> implements ModelMutator<CollectionBuilder<S>> {

        private final Class<? extends ComponentSpec<S>> componentType;
        private final MethodRuleDefinition<R> ruleDefinition;
        private final ModelRegistry modelRegistry;
        private final ModelReference<CollectionBuilder<S>> subject;
        private final Class<S> binaryType;

        public ComponentBinariesRule(ModelReference<CollectionBuilder<S>> subject, Class<? extends ComponentSpec<S>> componentType, Class<S> binaryType, MethodRuleDefinition<R> ruleDefinition, ModelRegistry modelRegistry) {
            this.subject = subject;
            this.componentType = componentType;
            this.binaryType = binaryType;
            this.ruleDefinition = ruleDefinition;
            this.modelRegistry = modelRegistry;

        }

        public ModelReference<CollectionBuilder<S>> getSubject() {
            return subject;
        }

        public void mutate(final CollectionBuilder<S> binaries, final Inputs inputs) {
            ComponentSpecContainer componentSpecs = inputs.get(0, ModelType.of(ComponentSpecContainer.class)).getInstance();
            final List<String> binariesAddedByRule = new ArrayList<String>();

            for(ComponentSpec<S> componentSpec : componentSpecs.withType(componentType)){
                CollectionBuilder<? extends S> collectionBuilder = new ActionAwareCollectionBuilder<S>(binaries, new Action<String>() {
                    public void execute(String binaryName) {
                        binariesAddedByRule.add(binaryName);
                    }
                });
                ruleDefinition.getRuleInvoker().invoke(collectionBuilder, componentSpec);

                for (String binaryAddedByRule : binariesAddedByRule) {
                    S binarySpec = modelRegistry.get(ModelPath.path(String.format("binaries.%s", binaryAddedByRule)), ModelType.of(binaryType));
                    componentSpec.getBinaries().add(binarySpec);
                }
            }
        }


        //handle other inputs taken from rule parameter
        public List<ModelReference<?>> getInputs() {
            return ImmutableList.<ModelReference<?>>of(ModelReference.of("componentSpecs", ComponentSpecContainer.class));
        }

        public ModelRuleDescriptor getDescriptor() {
            return ruleDefinition.getDescriptor();
        }
    }

    protected <R> void invalidModelRule(MethodRuleDefinition<R> ruleDefinition, InvalidComponentModelException e) {
        StringBuilder sb = new StringBuilder();
        ruleDefinition.getDescriptor().describeTo(sb);
        sb.append(" is not a valid ComponentBinaries model rule method.");
        throw new InvalidModelRuleDeclarationException(sb.toString(), e);
    }


    private class ActionAwareCollectionBuilder<T extends BinarySpec> implements CollectionBuilder<T> {
        private final CollectionBuilder<T> delegate;
        private final Action<String> callbackAction;

        public ActionAwareCollectionBuilder(CollectionBuilder<T> delegate, Action<String> callbackAction) {
            this.delegate = delegate;
            this.callbackAction = callbackAction;
        }

        public void create(String name) {
            delegate.create(name);
            callbackAction.execute(name);
        }

        public void create(String name, Action<? super T> configAction) {
            delegate.create(name, configAction);
            callbackAction.execute(name);

        }

        public <S extends T> void create(String name, Class<S> type) {
            delegate.create(name, type);
            callbackAction.execute(name);
        }

        public <S extends T> void create(String name, Class<S> type, Action<? super S> configAction) {
            delegate.create(name, type, configAction);
            callbackAction.execute(name);
        }
    }
}
