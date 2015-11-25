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
import com.google.common.collect.*;
import org.gradle.api.Action;
import org.gradle.api.Nullable;
import org.gradle.api.Transformer;
import org.gradle.internal.Cast;
import org.gradle.internal.Factories;
import org.gradle.model.ModelMap;
import org.gradle.model.RuleSource;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;
import org.gradle.model.internal.core.rule.describe.NestedModelRuleDescriptor;
import org.gradle.model.internal.manage.instance.ManagedInstance;
import org.gradle.model.internal.type.ModelType;

import java.util.Collection;
import java.util.Iterator;
import java.util.Set;

import static org.gradle.internal.Cast.uncheckedCast;

// TODO - mix Groovy DSL support in
public class NodeBackedModelMap<T> extends ModelMapGroovyView<T> implements ManagedInstance {

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
                if (!baseItemModelType.isAssignableFrom(type) || baseItemModelType.equals(type)) {
                    throw new IllegalArgumentException(String.format("%s is not a subtype of %s", type, baseItemModelType));
                }
                return nodeInitializerRegistry.getNodeInitializer(NodeInitializerContext.forType(type));
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
                    public Multimap<ModelActionRole, ModelAction> getActions(ModelReference<?> subject, ModelRuleDescriptor descriptor) {
                        return ImmutableSetMultimap.<ModelActionRole, ModelAction>builder()
                            .put(ModelActionRole.Discover, AddProjectionsAction.of(subject, descriptor,
                                UnmanagedModelProjection.of(type)
                            ))
                            .put(ModelActionRole.Create, DirectNodeNoInputsModelAction.of(subject, descriptor, new Action<MutableModelNode>() {
                                @Override
                                public void execute(MutableModelNode modelNode) {
                                    NamedEntityInstantiator<T> instantiator = instantiatorTransform.transform(modelNode.getParent());
                                    S item = instantiator.create(modelNode.getPath().getName(), type.getConcreteClass());
                                    modelNode.setPrivateData(type, item);
                                }
                            }))
                            .build();
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

    @Override
    public <S> void afterEach(Class<S> type, Action<? super S> configAction) {
        doFinalizeAll(ModelType.of(type), configAction);
    }

    // Called from transformed DSL rules
    public <S> void afterEach(Class<S> type, DeferredModelAction configAction) {
        doFinalizeAll(ModelType.of(type), configAction);
    }

    @Override
    public void afterEach(Action<? super T> configAction) {
        doFinalizeAll(elementType, configAction);
    }

    // Called from transformed DSL rules
    public void afterEach(DeferredModelAction configAction) {
        doFinalizeAll(elementType, configAction);
    }

    @Override
    public void all(Action<? super T> configAction) {
        viewState.assertCanMutate();
        ModelRuleDescriptor descriptor = NestedModelRuleDescriptor.append(sourceDescriptor, "all()");
        ModelReference<T> subject = ModelReference.of(elementType);
        modelNode.applyToAllLinks(ModelActionRole.Mutate, NoInputsModelAction.of(subject, descriptor, configAction));
    }

    // Called from transformed DSL rules
    public void all(DeferredModelAction configAction) {
        viewState.assertCanMutate();
        ModelReference<T> subject = ModelReference.of(elementType);
        modelNode.applyToAllLinks(ModelActionRole.Initialize, toInitializeAction(subject, configAction, ModelActionRole.Mutate));
    }

    @Override
    public void beforeEach(Action<? super T> configAction) {
        doBeforeEach(elementType, configAction);
    }

    // Called from transformed DSL rules
    public void beforeEach(DeferredModelAction configAction) {
        doBeforeEach(elementType, configAction);
    }

    @Override
    public <S> void beforeEach(Class<S> type, Action<? super S> configAction) {
        doBeforeEach(ModelType.of(type), configAction);
    }

    // Called from transformed DSL rules
    public <S> void beforeEach(Class<S> type, DeferredModelAction configAction) {
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
        doCreate(name, elementType, (Action<? super T>) null);
    }

    @Override
    public void create(String name, Action<? super T> configAction) {
        doCreate(name, elementType, configAction);
    }

    // Called from transformed DSL rules
    public void create(String name, DeferredModelAction configAction) {
        doCreate(name, elementType, configAction);
    }

    @Override
    public <S extends T> void create(String name, Class<S> type) {
        doCreate(name, ModelType.of(type), (Action<? super T>) null);
    }

    @Override
    public <S extends T> void create(String name, Class<S> type, Action<? super S> configAction) {
        doCreate(name, ModelType.of(type), configAction);
    }

    // Called from transformed DSL rules
    public <S extends T> void create(String name, Class<S> type, DeferredModelAction configAction) {
        doCreate(name, ModelType.of(type), configAction);
    }

    @Override
    public void put(String name, T instance) {
        Class<T> type = Cast.uncheckedCast(instance.getClass());
        ModelRuleDescriptor descriptor = NestedModelRuleDescriptor.append(sourceDescriptor, "put()");
        modelNode.addLink(
            ModelRegistrations.unmanagedInstance(
                ModelReference.of(modelNode.getPath().child(name), type),
                Factories.constant(instance)
            )
            .descriptor(descriptor)
            .build()
        );
    }

    private <S> void doBeforeEach(ModelType<S> type, Action<? super S> configAction) {
        viewState.assertCanMutate();
        ModelRuleDescriptor descriptor = NestedModelRuleDescriptor.append(sourceDescriptor, "beforeEach()");
        ModelReference<S> subject = ModelReference.of(type);
        modelNode.applyToAllLinks(ModelActionRole.Defaults, NoInputsModelAction.of(subject, descriptor, configAction));
    }

    private <S> void doBeforeEach(ModelType<S> type, DeferredModelAction configAction) {
        viewState.assertCanMutate();
        ModelReference<S> subject = ModelReference.of(type);
        modelNode.applyToAllLinks(ModelActionRole.Defaults, toInitializeAction(subject, configAction, ModelActionRole.Defaults));
    }

    private <S extends T> void doCreate(String name, ModelType<S> type, DeferredModelAction action) {
        ModelPath childPath = modelNode.getPath().child(name);
        doCreate(childPath, type, action.getDescriptor(), toInitializeAction(ModelReference.of(childPath, type), action, ModelActionRole.Initialize));
    }

    private <S extends T> void doCreate(String name, ModelType<S> type, @Nullable Action<? super S> initAction) {
        ModelPath childPath = modelNode.getPath().child(name);
        ModelRuleDescriptor descriptor = NestedModelRuleDescriptor.append(sourceDescriptor, "create(%s)", name);
        if (initAction != null) {
            doCreate(childPath, type, descriptor, NoInputsModelAction.of(ModelReference.of(childPath, type), descriptor, initAction));
        } else {
            doCreate(childPath, type, descriptor, null);
        }
    }

    private <S extends T> void doCreate(ModelPath childPath, ModelType<S> type, ModelRuleDescriptor descriptor, @Nullable ModelAction initAction) {
        viewState.assertCanMutate();
        NodeInitializer nodeInitializer = creatorStrategy.initializer(type);

        ModelRegistrations.Builder builder = ModelRegistrations.of(childPath, nodeInitializer).descriptor(descriptor);
        if (initAction != null) {
            builder.action(ModelActionRole.Initialize, initAction);
        }
        ModelRegistration registration = builder.build();

        modelNode.addLink(registration);

        if (eager) {
            //noinspection ConstantConditions
            modelNode.getLink(childPath.getName()).ensureUsable();
        }
    }

    private <S> void doFinalizeAll(ModelType<S> type, Action<? super S> configAction) {
        viewState.assertCanMutate();
        ModelRuleDescriptor descriptor = NestedModelRuleDescriptor.append(sourceDescriptor, "afterEach()");
        ModelReference<S> subject = ModelReference.of(type);
        modelNode.applyToAllLinks(ModelActionRole.Finalize, NoInputsModelAction.of(subject, descriptor, configAction));
    }

    private <S> void doFinalizeAll(ModelType<S> type, DeferredModelAction configAction) {
        viewState.assertCanMutate();
        ModelReference<S> subject = ModelReference.of(type);
        modelNode.applyToAllLinks(ModelActionRole.Initialize, toInitializeAction(subject, configAction, ModelActionRole.Finalize));
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
            return link.asMutable(elementType, sourceDescriptor).getInstance();
        } else {
            return link.asImmutable(elementType, sourceDescriptor).getInstance();
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
    public void named(String name, Action<? super T> configAction) {
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

    // Called from transformed DSL rules
    public void named(String name, final DeferredModelAction action) {
        viewState.assertCanMutate();
        ModelReference<?> subject = ModelReference.of(modelNode.getPath().child(name));
        modelNode.applyToLink(ModelActionRole.Initialize, toInitializeAction(subject, action, ModelActionRole.Mutate));
    }

    private ModelAction toInitializeAction(ModelReference<?> subject, final DeferredModelAction action, final ModelActionRole role) {
        return DirectNodeNoInputsModelAction.of(subject, action.getDescriptor(), new Action<MutableModelNode>() {
            @Override
            public void execute(MutableModelNode node) {
                action.execute(node, role);
            }
        });
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
        return ModelMap.class.getSimpleName() + '<' + elementType.getDisplayName() + "> '" + modelNode.getPath() + "'";
    }

    public <S extends T> ModelMap<S> toSubType(Class<S> type) {
        // TODO:HH Filtering should be additive
        // map.withType(Foo).withType(Bar) should return only elements that implement both Foo and Bar
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
        viewState.assertCanMutate();
        ModelRuleDescriptor descriptor = NestedModelRuleDescriptor.append(sourceDescriptor, "withType()");
        ModelReference<S> subject = ModelReference.of(type);
        modelNode.applyToAllLinks(ModelActionRole.Mutate, NoInputsModelAction.of(subject, descriptor, configAction));
    }

    // Called from transformed DSL rules
    public <S> void withType(Class<S> type, DeferredModelAction configAction) {
        viewState.assertCanMutate();
        ModelReference<S> subject = ModelReference.of(type);
        modelNode.applyToAllLinks(ModelActionRole.Initialize, toInitializeAction(subject, configAction, ModelActionRole.Mutate));
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

    @Override
    public Void methodMissing(String name, Object argsObj) {
        Object[] args = (Object[]) argsObj;
        if (args.length == 2 && args[0] instanceof Class<?> && args[1] instanceof DeferredModelAction) {
            // Called from transformed DSL rules
            Class<? extends T> itemType = uncheckedCast(args[0]);
            DeferredModelAction action = uncheckedCast(args[1]);
            doCreate(name, ModelType.of(itemType), action);
            return null;
        }
        if (args.length == 1 && args[0] instanceof DeferredModelAction) {
            // Called from transformed DSL rules
            DeferredModelAction action = uncheckedCast(args[0]);
            named(name, action);
            return null;
        }
        return super.methodMissing(name, argsObj);
    }
}
