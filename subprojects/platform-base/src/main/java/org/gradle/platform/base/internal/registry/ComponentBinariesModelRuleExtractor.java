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
import org.gradle.api.Named;
import org.gradle.api.PolymorphicDomainObjectContainer;
import org.gradle.internal.Actions;
import org.gradle.internal.TriAction;
import org.gradle.language.base.plugins.ComponentModelBasePlugin;
import org.gradle.model.InvalidModelRuleDeclarationException;
import org.gradle.model.ModelMap;
import org.gradle.model.internal.core.*;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;
import org.gradle.model.internal.core.rule.describe.NestedModelRuleDescriptor;
import org.gradle.model.internal.inspect.MethodRuleDefinition;
import org.gradle.model.internal.type.ModelType;
import org.gradle.platform.base.*;
import org.gradle.platform.base.internal.ComponentSpecInternal;

import java.util.List;

import static org.gradle.internal.Cast.uncheckedCast;

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
            TriAction<MutableModelNode, C, List<ModelView<?>>> action = new TriAction<MutableModelNode, C, List<ModelView<?>>>() {
                @Override
                public void execute(MutableModelNode componentModelNode, C component, List<ModelView<?>> componentRuleInputs) {
                    ComponentSpecInternal componentSpecInternal = uncheckedCast(componentModelNode.getPrivateData());
                    MutableModelNode binariesNode = componentModelNode.getLink("binaries");
                    DefaultModelViewState binariesState = new DefaultModelViewState(DefaultModelMap.modelMapTypeOf(binaryType), getDescriptor());
                    ModelMap<BinarySpec> binarySpecs = new ModelMapGroovyDecorator<BinarySpec>(
                        new PolymorphicDomainObjectContainerBackedModelMap<BinarySpec>(
                            componentSpecInternal.getBinaries(), ModelType.of(BinarySpec.class), binariesNode, getDescriptor()
                        ),
                        binariesState
                    );
                    try {
                        invoke(componentRuleInputs, binarySpecs.withType(binaryType), componentSpecInternal);
                    } finally {
                        binariesState.close();
                    }
                }
            };
            modelNode.applyToAllLinks(ModelActionRole.Mutate, TriActionBackedModelAction.of(ModelReference.of(ModelType.of(componentType)), getDescriptor(), getInputs(), action));
        }
    }

    protected InvalidModelRuleDeclarationException invalidModelRule(MethodRuleDefinition<?, ?> ruleDefinition, InvalidModelException e) {
        StringBuilder sb = new StringBuilder();
        ruleDefinition.getDescriptor().describeTo(sb);
        sb.append(" is not a valid ComponentBinaries model rule method.");
        return new InvalidModelRuleDeclarationException(sb.toString(), e);
    }

    private static class PolymorphicDomainObjectContainerBackedModelMap<T extends Named> extends AbstractModelMap<T> {

        private final PolymorphicDomainObjectContainer<T> container;
        private Class<T> elementClass;

        private PolymorphicDomainObjectContainerBackedModelMap(PolymorphicDomainObjectContainer<T> container, ModelType<T> elementType, MutableModelNode modelNode,
                                                               ModelRuleDescriptor sourceDescriptor) {
            super(elementType, modelNode, sourceDescriptor);
            this.container = container;
            this.elementClass = elementType.getConcreteClass();
        }

        @Override
        public <S> ModelMap<S> withType(Class<S> type) {
            if (type.equals(elementClass)) {
                return uncheckedCast(this);
            }

            if (elementClass.isAssignableFrom(type)) {
                Class<? extends T> castType = uncheckedCast(type);
                ModelMap<? extends T> subType = toSubtype(castType);
                return uncheckedCast(subType);
            }

            return new DefaultModelMap<S>(ModelType.of(type), sourceDescriptor, modelNode, new ChildNodeCreatorStrategy<S>() {
                @Override
                public <D extends S> ModelCreator creator(MutableModelNode parentNode, ModelRuleDescriptor sourceDescriptor, ModelType<D> type, String name) {
                    throw new IllegalArgumentException(String.format("Cannot create an item of type %s as this is not a subtype of %s.", type, elementType.toString()));
                }
            });
        }

        private <S extends T> ModelMap<S> toSubtype(Class<S> itemSubtype) {
            PolymorphicDomainObjectContainer<S> castContainer = uncheckedCast(container);
            return new PolymorphicDomainObjectContainerBackedModelMap<S>(castContainer, ModelType.of(itemSubtype), modelNode, sourceDescriptor);
        }

        @Override
        public boolean containsValue(Object item) {
            //noinspection SuspiciousMethodCalls
            return container.contains(item);
        }

        @Override
        public void create(String name) {
            create(name, elementClass);
        }

        @Override
        public void create(String name, Action<? super T> configAction) {
            create(name, elementClass, configAction);
        }

        @Override
        public <S extends T> void create(String name, Class<S> type) {
            create(name, type, Actions.doNothing());
        }

        @Override
        public <S extends T> void create(String name, Class<S> type, Action<? super S> configAction) {
            ModelPath childPath = modelNode.getPath().child(name);
            ModelReference<S> childReference = ModelReference.of(childPath, type);
            ModelRuleDescriptor descriptor = NestedModelRuleDescriptor.append(sourceDescriptor, "create(%s)", name);
            modelNode.applyToLink(ModelActionRole.Defaults, ActionBackedModelAction.of(childReference, descriptor, configAction));
            container.create(name, type);
        }
    }
}
