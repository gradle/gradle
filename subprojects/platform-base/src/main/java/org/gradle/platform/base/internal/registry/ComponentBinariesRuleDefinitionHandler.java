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

@SuppressWarnings("unchecked")
public class ComponentBinariesRuleDefinitionHandler extends AbstractAnnotationDrivenMethodComponentRuleDefinitionHandler<ComponentBinaries> {

    public <R> void register(final MethodRuleDefinition<R> ruleDefinition, final ModelRegistry modelRegistry, RuleSourceDependencies dependencies) {
        try {
            verifyMethodSignature(ruleDefinition);
            final Class<? extends BinarySpec> binaryType = (Class<? extends BinarySpec>) ruleDefinition.getReferences().get(0).getType().getTypeVariables().get(0).getConcreteClass();
            final Class<? extends ComponentSpec> componentType = getComponentType(binaryType, ruleDefinition);

            dependencies.add(ComponentModelBasePlugin.class);
            final ModelReference<CollectionBuilder<? extends BinarySpec>> subject = ModelReference.of(ModelPath.path("binaries"), new ModelType<CollectionBuilder<? extends BinarySpec>>() {
            });

            modelRegistry.mutate(new ComponentBinariesRule(subject, componentType, binaryType, ruleDefinition, modelRegistry));

        } catch (InvalidComponentModelException e) {
            invalidModelRule(ruleDefinition, e);
        }
    }

    private <R> void verifyMethodSignature(MethodRuleDefinition<R> ruleDefinition) {
        assertIsVoidMethod(ruleDefinition);
        assertHasCollectionBuilderSubject(ruleDefinition, BinarySpec.class);
    }


    @SuppressWarnings("unchecked")
    private <R> Class<? extends ComponentSpec> getComponentType(Class<? extends BinarySpec> expectedBinaryType, MethodRuleDefinition<R> ruleDefinition) {
        List<ModelReference<?>> references = ruleDefinition.getReferences();
        Class<?> componentType = null;
        for (ModelReference<?> reference : references) {
            if (ComponentSpec.class.isAssignableFrom(reference.getType().getConcreteClass())) {
                if (componentType != null) {
                    throw new InvalidComponentModelException(String.format("%s method must have one parameter extending %s. Found multiple parameter extending %s.", annotationType.getSimpleName(),
                            ComponentSpec.class.getSimpleName(),
                            ComponentSpec.class.getSimpleName()));
                }
                componentType = reference.getType().getConcreteClass();
            }
        }

        validateComponentType(expectedBinaryType, componentType);
        return (Class<? extends ComponentSpec>) componentType;
    }

    private void validateComponentType(Class<? extends BinarySpec> expectedBinaryType, Class<?> componentType) {
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
                        if (((Class) givenBinaryType).isAssignableFrom(expectedBinaryType)) {
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

    @SuppressWarnings("unchecked")
    private class ComponentBinariesRule<R, T extends BinarySpec> implements ModelMutator<CollectionBuilder<T>> {

        private final Class<? extends ComponentSpec> componentType;
        private final MethodRuleDefinition<R> ruleDefinition;
        private final ModelRegistry modelRegistry;
        private final ModelReference<CollectionBuilder<T>> subject;
        private final Class<T> binaryType;

        public ComponentBinariesRule(ModelReference<CollectionBuilder<T>> subject,
                                     Class<? extends ComponentSpec> componentType,
                                     Class<T> binaryType,
                                     MethodRuleDefinition<R> ruleDefinition, ModelRegistry modelRegistry) {
            this.subject = subject;
            this.componentType = componentType;
            this.binaryType = binaryType;
            this.ruleDefinition = ruleDefinition;
            this.modelRegistry = modelRegistry;
        }

        public ModelReference<CollectionBuilder<T>> getSubject() {
            return subject;
        }

        public void mutate(final CollectionBuilder<T> binaries, final Inputs inputs) {
            ComponentSpecContainer componentSpecs = inputs.get(0, ModelType.of(ComponentSpecContainer.class)).getInstance();
            final List<String> binariesAddedByRule = new ArrayList<String>();

            //don't use all. this should explicitly executed on types _IN_ the container now
            componentSpecs.withType(componentType).all(new Action<ComponentSpec>() {
                public void execute(ComponentSpec componentSpec) {
                    CollectionBuilder<? extends ComponentSpec> collectionBuilder = new ActionAwareCollectionBuilder(binaries, new Action<String>() {
                        public void execute(String binaryName) {
                            binariesAddedByRule.add(binaryName);
                        }
                    });
                    ruleDefinition.getRuleInvoker().invoke(collectionBuilder, componentSpec);

                    for (String binaryAddedByRule : binariesAddedByRule) {
                        T binarySpec = modelRegistry.get(ModelPath.path(String.format("binaries.%s", binaryAddedByRule)), ModelType.of(binaryType));
                        componentSpec.getBinaries().add(binarySpec);
                    }
                }

            });
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
