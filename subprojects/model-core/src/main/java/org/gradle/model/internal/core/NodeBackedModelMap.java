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

package org.gradle.model.internal.core;

import com.google.common.base.Function;
import com.google.common.collect.Iterables;
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import org.gradle.api.Action;
import org.gradle.api.Nullable;
import org.gradle.api.Transformer;
import org.gradle.internal.Actions;
import org.gradle.model.ModelMap;
import org.gradle.model.RuleSource;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;
import org.gradle.model.internal.core.rule.describe.NestedModelRuleDescriptor;
import org.gradle.model.internal.manage.instance.ManagedInstance;
import org.gradle.model.internal.type.ModelType;

import java.util.*;

import static org.gradle.internal.Cast.uncheckedCast;

public class NodeBackedModelMap<T> implements ModelMap<T>, ManagedInstance {

    private final ModelType<T> elementType;
    private final ModelRuleDescriptor sourceDescriptor;
    private final MutableModelNode modelNode;
    private final String description;
    private final boolean eager;
    private final ModelViewState viewState;
    private final ChildNodeInitializerStrategy<? super T> creatorStrategy;

    public NodeBackedModelMap(String description, ModelType<T> elementType, ModelRuleDescriptor sourceDescriptor, MutableModelNode modelNode, boolean eager, ModelViewState viewState, ChildNodeInitializerStrategy<? super T> creatorStrategy) {
        this.description = description;
        this.eager = eager;
        this.viewState = viewState;
        this.creatorStrategy = creatorStrategy;
        this.elementType = elementType;
        this.modelNode = modelNode;
        this.sourceDescriptor = sourceDescriptor;
    }

    public NodeBackedModelMap(ModelType<T> type, ModelRuleDescriptor sourceDescriptor, MutableModelNode modelNode, boolean eager, ModelViewState viewState, ChildNodeInitializerStrategy<? super T> childStrategy) {
        this(derivedDescription(modelNode, type), type, sourceDescriptor, modelNode, eager, viewState, childStrategy);
    }

    public static <T> ChildNodeInitializerStrategy<T> createUsingRegistry(final ModelType<T> baseItemModelType, final NodeInitializerRegistry nodeInitializerRegistry) {
        return new ChildNodeInitializerStrategy<T>() {
            @Override
            public <S extends T> NodeInitializer initializer(ModelType<S> type) {
                if (baseItemModelType.asSubclass(type) == null) {
                    throw new IllegalArgumentException(String.format("%s is not a subtype of %s", type, baseItemModelType));
                }
                NodeInitializer nodeInitializer = nodeInitializerRegistry.getNodeInitializer(type);
                return nodeInitializer;
            }
        };
    }

    public static <T> ChildNodeInitializerStrategy<T> createUsingParentNode(final ModelType<T> baseItemModelType) {
        return createUsingParentNode(new Transformer<NamedEntityInstantiator<T>, MutableModelNode>() {
            @Override
            public NamedEntityInstantiator<T> transform(MutableModelNode modelNode) {
                return modelNode.getPrivateData(instantiatorTypeOf(baseItemModelType));
            }
        });
    }

    public static <T> ChildNodeInitializerStrategy<T> createUsingParentNode(final Transformer<? extends NamedEntityInstantiator<T>, ? super MutableModelNode> instantiatorTransform) {
        return new ChildNodeInitializerStrategy<T>() {
            @Override
            public <S extends T> NodeInitializer initializer(final ModelType<S> type) {
                return new NodeInitializer() {
                    @Override
                    public List<? extends ModelReference<?>> getInputs() {
                        return Collections.emptyList();
                    }

                    @Override
                    public void execute(MutableModelNode modelNode, List<ModelView<?>> inputs) {
                        NamedEntityInstantiator<T> instantiator = instantiatorTransform.transform(modelNode.getParent());
                        S item = instantiator.create(modelNode.getPath().getName(), type.getConcreteClass());
                        modelNode.setPrivateData(type, item);
                    }

                    @Override
                    public List<? extends ModelProjection> getProjections() {
                        return Collections.singletonList(UnmanagedModelProjection.of(type));
                    }
                };
            }
        };
    }

    public static <I> ModelType<NamedEntityInstantiator<I>> instantiatorTypeOf(ModelType<I> type) {
        return new ModelType.Builder<NamedEntityInstantiator<I>>() {
        }.where(
            new ModelType.Parameter<I>() {
            }, type
        ).build();
    }

    @Override
    public MutableModelNode getBackingNode() {
        return modelNode;
    }

    @Override
    public ModelType<?> getManagedType() {
        return ModelType.of(this.getClass());
    }

    public <S> void afterEach(Class<S> type, Action<? super S> configAction) {
        doFinalizeAll(ModelType.of(type), configAction);
    }

    @Override
    public void afterEach(Action<? super T> configAction) {
        doFinalizeAll(elementType, configAction);
    }

    @Override
    public void all(final Action<? super T> configAction) {
        viewState.assertCanMutate();
        ModelRuleDescriptor descriptor = NestedModelRuleDescriptor.append(sourceDescriptor, "all()");
        ModelReference<T> subject = ModelReference.of(elementType);
        modelNode.applyToAllLinks(ModelActionRole.Mutate, NoInputsModelAction.of(subject, descriptor, configAction));
    }

    @Override
    public void beforeEach(Action<? super T> configAction) {
        doBeforeEach(elementType, configAction);
    }

    @Override
    public <S> void beforeEach(Class<S> type, Action<? super S> configAction) {
        doBeforeEach(ModelType.of(type), configAction);
    }

    @Override
    public boolean containsKey(Object name) {
        viewState.assertCanReadChildren();
        return name instanceof String && modelNode.hasLink((String) name, elementType);
    }

    @Override
    public boolean containsValue(Object item) {
        throw new UnsupportedOperationException("Not implemented yet.");
    }

    @Override
    public void create(final String name) {
        doCreate(name, elementType, Actions.doNothing());
    }

    @Override
    public void create(String name, Action<? super T> configAction) {
        doCreate(name, elementType, configAction);
    }

    @Override
    public <S extends T> void create(final String name, final Class<S> type) {
        doCreate(name, ModelType.of(type), Actions.doNothing());
    }

    @Override
    public <S extends T> void create(final String name, final Class<S> type, final Action<? super S> configAction) {
        doCreate(name, ModelType.of(type), configAction);
    }

    private <S> void doBeforeEach(ModelType<S> type, Action<? super S> configAction) {
        viewState.assertCanMutate();
        ModelRuleDescriptor descriptor = NestedModelRuleDescriptor.append(sourceDescriptor, "beforeEach()");
        ModelReference<S> subject = ModelReference.of(type);
        modelNode.applyToAllLinks(ModelActionRole.Defaults, NoInputsModelAction.of(subject, descriptor, configAction));
    }

    private <S extends T> void doCreate(final String name, final ModelType<S> type, final Action<? super S> initAction) {
        viewState.assertCanMutate();
        ModelPath childPath = modelNode.getPath().child(name);
        ModelRuleDescriptor descriptor = NestedModelRuleDescriptor.append(sourceDescriptor, "create(%s)", name);

        NodeInitializer nodeInitializer = creatorStrategy.initializer(type);

        ModelCreator creator = ModelCreators.of(childPath, nodeInitializer)
            .descriptor(descriptor)
            .action(ModelActionRole.Initialize, NoInputsModelAction.of(ModelReference.of(childPath, type), descriptor, initAction))
            .build();

        modelNode.addLink(creator);

        if (eager) {
            //noinspection ConstantConditions
            modelNode.getLink(name).ensureUsable();
        }
    }

    private <S> void doFinalizeAll(ModelType<S> type, Action<? super S> configAction) {
        viewState.assertCanMutate();
        ModelRuleDescriptor descriptor = NestedModelRuleDescriptor.append(sourceDescriptor, "afterEach()");
        ModelReference<S> subject = ModelReference.of(type);
        modelNode.applyToAllLinks(ModelActionRole.Finalize, NoInputsModelAction.of(subject, descriptor, configAction));
    }

    @Nullable
    @Override
    public T get(Object name) {
        return get((String) name);
    }

    @Nullable
    @Override
    public T get(String name) {
        viewState.assertCanReadChildren();

        // TODO - lock this down
        MutableModelNode link = modelNode.getLink(name);
        if (link == null) {
            return null;
        }
        link.ensureUsable();
        if (viewState.isCanMutate()) {
            return link.asWritable(elementType, sourceDescriptor, null).getInstance();
        } else {
            return link.asReadOnly(elementType, sourceDescriptor).getInstance();
        }
    }

    @Override
    public boolean isEmpty() {
        return size() == 0;
    }

    @Override
    public Set<String> keySet() {
        viewState.assertCanReadChildren();
        return modelNode.getLinkNames(elementType);
    }

    @Override
    public void named(final String name, Action<? super T> configAction) {
        viewState.assertCanMutate();
        ModelRuleDescriptor descriptor = NestedModelRuleDescriptor.append(sourceDescriptor, "named(%s)", name);
        ModelReference<T> subject = ModelReference.of(modelNode.getPath().child(name), elementType);
        modelNode.applyToLink(ModelActionRole.Mutate, NoInputsModelAction.of(subject, descriptor, configAction));
    }

    @Override
    public void named(String name, Class<? extends RuleSource> ruleSource) {
        viewState.assertCanMutate();
        modelNode.applyToLink(name, ruleSource);
    }

    @Override
    public int size() {
        viewState.assertCanReadChildren();
        return modelNode.getLinkCount(elementType);
    }

    @Override
    public String toString() {
        return description;
    }

    private static String derivedDescription(ModelNode modelNode, ModelType<?> elementType) {
        return ModelMap.class.getSimpleName() + '<' + elementType.getSimpleName() + "> '" + modelNode.getPath() + "'";
    }

    public <S extends T> ModelMap<S> toSubType(Class<S> type) {
        ChildNodeInitializerStrategy<S> creatorStrategy = uncheckedCast(this.creatorStrategy);
        return new NodeBackedModelMap<S>(ModelType.of(type), sourceDescriptor, modelNode, eager, viewState, creatorStrategy);
    }

    @Override
    public Collection<T> values() {
        viewState.assertCanReadChildren();
        Iterable<T> values = Iterables.transform(keySet(), new Function<String, T>() {
            public T apply(@Nullable String name) {
                return get(name);
            }
        });
        return Lists.newArrayList(values);
    }

    @Override
    public Iterator<T> iterator() {
        viewState.assertCanReadChildren();
        return Iterators.transform(keySet().iterator(), new Function<String, T>() {
            @Override
            public T apply(@Nullable String name) {
                return get(name);
            }
        });
    }

    @Override
    public <S> void withType(Class<S> type, Action<? super S> configAction) {
        ModelRuleDescriptor descriptor = NestedModelRuleDescriptor.append(sourceDescriptor, "withType()");
        ModelReference<S> subject = ModelReference.of(type);
        viewState.assertCanMutate();
        modelNode.applyToAllLinks(ModelActionRole.Mutate, NoInputsModelAction.of(subject, descriptor, configAction));
    }

    @Override
    public <S> void withType(Class<S> type, Class<? extends RuleSource> rules) {
        viewState.assertCanMutate();
        modelNode.applyToLinks(ModelType.of(type), rules);
    }

    @Override
    public <S> ModelMap<S> withType(Class<S> type) {
        if (type.equals(elementType.getConcreteClass())) {
            return uncheckedCast(this);
        }

        if (elementType.getConcreteClass().isAssignableFrom(type)) {
            Class<? extends T> castType = uncheckedCast(type);
            ModelMap<? extends T> subType = toSubType(castType);
            return uncheckedCast(subType);
        }

        return new NodeBackedModelMap<S>(ModelType.of(type), sourceDescriptor, modelNode, eager, viewState, new ChildNodeInitializerStrategy<S>() {
            @Override
            public <D extends S> NodeInitializer initializer(ModelType<D> type) {
                throw new IllegalArgumentException(String.format("Cannot create an item of type %s as this is not a subtype of %s.", type, elementType.toString()));
            }
        });
    }

}
