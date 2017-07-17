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
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import org.gradle.api.Action;
import org.gradle.api.Transformer;
import org.gradle.api.specs.Spec;
import org.gradle.internal.Cast;
import org.gradle.internal.Factories;
import org.gradle.model.InvalidModelRuleException;
import org.gradle.model.ModelMap;
import org.gradle.model.ModelRuleBindingException;
import org.gradle.model.RuleSource;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;
import org.gradle.model.internal.inspect.ModelElementProjection;
import org.gradle.model.internal.manage.instance.ManagedInstance;
import org.gradle.model.internal.report.IncompatibleTypeReferenceReporter;
import org.gradle.model.internal.type.ModelType;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import static org.gradle.internal.Cast.uncheckedCast;
import static org.gradle.model.internal.core.NodeInitializerContext.forExtensibleType;
import static org.gradle.model.internal.core.NodePredicate.allLinks;

// TODO - mix Groovy DSL support in
public class NodeBackedModelMap<T> extends ModelMapGroovyView<T> implements ManagedInstance {

    private static final ElementFilter NO_PARENT = new ElementFilter(ModelType.UNTYPED) {
        @Override
        public boolean apply(MutableModelNode node) {
            return true;
        }

        @Override
        public boolean isSatisfiedBy(ModelType<?> element) {
            return true;
        }

        @Override
        public void validateCanBindAction(MutableModelNode node, ModelAction action) {}

        @Override
        public void validateCanCreateElement(ModelPath path, ModelType<?> type) {}
    };

    private final ModelType<T> elementType;
    private final ModelRuleDescriptor sourceDescriptor;
    private final MutableModelNode modelNode;
    private final ModelViewState viewState;
    private final ChildNodeInitializerStrategy<? super T> creatorStrategy;
    private final ElementFilter elementFilter;
    private final ModelType<?> publicType;

    // Note: used by generated subtypes
    public NodeBackedModelMap(ModelType<?> publicType, ModelType<T> elementType, ModelRuleDescriptor sourceDescriptor, MutableModelNode modelNode,
                              ModelViewState viewState, ChildNodeInitializerStrategy<? super T> creatorStrategy) {
        this(publicType, elementType, sourceDescriptor, modelNode, viewState, NO_PARENT, creatorStrategy);
    }

    private NodeBackedModelMap(ModelType<?> publicType, ModelType<T> elementType, ModelRuleDescriptor sourceDescriptor, MutableModelNode modelNode,
                               ModelViewState viewState, ElementFilter parentFilter, ChildNodeInitializerStrategy<? super T> creatorStrategy) {
        this.publicType = publicType;
        this.viewState = viewState;
        this.creatorStrategy = creatorStrategy;
        this.elementType = elementType;
        this.modelNode = modelNode;
        this.sourceDescriptor = sourceDescriptor;
        this.elementFilter = parentFilter.withType(elementType);
    }

    public static <T> ChildNodeInitializerStrategy<T> createUsingRegistry(final NodeInitializerRegistry nodeInitializerRegistry) {
        return new ChildNodeInitializerStrategy<T>() {
            @Override
            public <S extends T> NodeInitializer initializer(ModelType<S> type, Spec<ModelType<?>> constraints) {
                return nodeInitializerRegistry.getNodeInitializer(forExtensibleType(type, constraints));
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
            public <S extends T> NodeInitializer initializer(final ModelType<S> type, Spec<ModelType<?>> constraints) {
                return new NodeInitializer() {
                    @Override
                    public Multimap<ModelActionRole, ModelAction> getActions(ModelReference<?> subject, ModelRuleDescriptor descriptor) {
                        return ImmutableSetMultimap.<ModelActionRole, ModelAction>builder()
                            .put(ModelActionRole.Discover, AddProjectionsAction.of(subject, descriptor,
                                UnmanagedModelProjection.of(type),
                                new ModelElementProjection(type)
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

    private static <I> ModelType<NamedEntityInstantiator<I>> instantiatorTypeOf(ModelType<I> type) {
        return new ModelType.Builder<NamedEntityInstantiator<I>>() {
        }.where(
            new ModelType.Parameter<I>() {
            }, type
        ).build();
    }

    @Override
    public String getName() {
        return modelNode.getPath().getName();
    }

    @Override
    public MutableModelNode getBackingNode() {
        return modelNode;
    }

    @Override
    public ModelType<?> getManagedType() {
        return ModelType.of(this.getClass());
    }

    private <E> void mutateChildren(ModelActionRole role, ModelType<E> filterType, String operation, Action<? super E> configAction) {
        viewState.assertCanMutate();
        ModelRuleDescriptor descriptor = sourceDescriptor.append(operation);
        ModelReference<E> subject = ModelReference.of(filterType);
        modelNode.applyTo(allLinks(elementFilter.withType(filterType)), role, NoInputsModelAction.of(subject, descriptor, configAction));
    }

    private <E> void mutateChildren(ModelActionRole role, ModelType<E> filterType, DeferredModelAction configAction) {
        viewState.assertCanMutate();
        ModelReference<E> subject = ModelReference.of(filterType);
        modelNode.defineRulesFor(allLinks(elementFilter.withType(filterType)), role, new DeferredActionWrapper<E>(subject, role, configAction));
    }

    @Override
    public <S> void afterEach(Class<S> type, Action<? super S> configAction) {
        mutateChildren(ModelActionRole.Finalize, ModelType.of(type), "afterEach()", configAction);
    }

    // Called from transformed DSL rules
    public <S> void afterEach(Class<S> type, DeferredModelAction configAction) {
        mutateChildren(ModelActionRole.Finalize, ModelType.of(type), configAction);
    }

    @Override
    public void afterEach(Action<? super T> configAction) {
        mutateChildren(ModelActionRole.Finalize, elementType, "afterEach()", configAction);
    }

    // Called from transformed DSL rules
    public void afterEach(DeferredModelAction configAction) {
        mutateChildren(ModelActionRole.Finalize, elementType, configAction);
    }

    @Override
    public void all(Action<? super T> configAction) {
        mutateChildren(ModelActionRole.Mutate, elementType, "all()", configAction);
    }

    // Called from transformed DSL rules
    public void all(DeferredModelAction configAction) {
        mutateChildren(ModelActionRole.Initialize, elementType, configAction);
    }

    @Override
    public void beforeEach(Action<? super T> configAction) {
        mutateChildren(ModelActionRole.Defaults, elementType, "beforeEach()", configAction);
    }

    // Called from transformed DSL rules
    public void beforeEach(DeferredModelAction configAction) {
        mutateChildren(ModelActionRole.Defaults, elementType, configAction);
    }

    @Override
    public <S> void beforeEach(Class<S> type, Action<? super S> configAction) {
        mutateChildren(ModelActionRole.Defaults, ModelType.of(type), "beforeEach()", configAction);
    }

    // Called from transformed DSL rules
    public <S> void beforeEach(Class<S> type, DeferredModelAction configAction) {
        mutateChildren(ModelActionRole.Defaults, ModelType.of(type), configAction);
    }

    @Override
    public boolean containsKey(Object name) {
        if (!(name instanceof String)) {
            viewState.assertCanReadChildren();
            return false;
        }
        viewState.assertCanReadChild((String) name);
        return modelNode.hasLink((String) name, elementFilter);
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
        ModelRuleDescriptor descriptor = sourceDescriptor.append("put()");
        if (instance instanceof ManagedInstance) {
            ManagedInstance target = (ManagedInstance) instance;
            modelNode.addReference(name, target.getManagedType(), target.getBackingNode(), descriptor);
        } else {
            modelNode.addLink(
                ModelRegistrations.unmanagedInstance(
                    ModelReference.of(modelNode.getPath().child(name), type),
                    Factories.constant(instance)
                )
                .descriptor(descriptor)
                .build()
            );
        }
    }

    private <S extends T> void doCreate(String name, ModelType<S> type, final DeferredModelAction action) {
        ModelPath childPath = modelNode.getPath().child(name);
        doCreate(childPath, type, action.getDescriptor(), DirectNodeNoInputsModelAction.of(ModelReference.of(childPath, type), action.getDescriptor(), new Action<MutableModelNode>() {
            @Override
            public void execute(MutableModelNode node) {
                action.execute(node, ModelActionRole.Initialize);
            }
        }));
    }

    private <S extends T> void doCreate(String name, ModelType<S> type, @Nullable Action<? super S> initAction) {
        ModelPath childPath = modelNode.getPath().child(name);
        ModelRuleDescriptor descriptor = sourceDescriptor.append("create(%s)", name);
        if (initAction != null) {
            doCreate(childPath, type, descriptor, NoInputsModelAction.of(ModelReference.of(childPath, type), descriptor, initAction));
        } else {
            doCreate(childPath, type, descriptor, null);
        }
    }

    private <S extends T> void doCreate(ModelPath childPath, ModelType<S> type, ModelRuleDescriptor descriptor, @Nullable ModelAction initAction) {
        viewState.assertCanMutate();
        elementFilter.validateCanCreateElement(childPath, type);

        NodeInitializer nodeInitializer = creatorStrategy.initializer(type, elementFilter);

        ModelRegistrations.Builder builder = ModelRegistrations.of(childPath, nodeInitializer).descriptor(descriptor);
        if (initAction != null) {
            builder.action(ModelActionRole.Initialize, initAction);
        }
        ModelRegistration registration = builder.build();

        modelNode.addLink(registration);
    }

    @Nullable
    @Override
    public T get(Object name) {
        return get((String) name);
    }

    @Nullable
    @Override
    public T get(String name) {
        // TODO - lock this down
        MutableModelNode link = modelNode.getLink(name);
        if (link == null) {
            return null;
        }

        viewState.assertCanReadChild(name);

        link.ensureUsable();
        if (!elementFilter.apply(link)) {
            return null;
        }
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
        return ImmutableSet.copyOf(modelNode.getLinkNames(elementFilter));
    }

    @Override
    public int size() {
        viewState.assertCanReadChildren();
        return modelNode.getLinkCount(elementFilter);
    }

    @Override
    public void named(String name, Action<? super T> configAction) {
        viewState.assertCanMutate();
        ModelRuleDescriptor descriptor = sourceDescriptor.append("named(%s)", name);
        ModelReference<T> subject = ModelReference.of(modelNode.getPath().child(name), elementType);
        modelNode.applyToLink(ModelActionRole.Mutate, new FilteringActionWrapper<T>(elementFilter, subject, NoInputsModelAction.of(subject, descriptor, configAction)));
    }

    @Override
    public void named(String name, Class<? extends RuleSource> ruleSource) {
        viewState.assertCanMutate();
        ModelRuleDescriptor descriptor = sourceDescriptor.append("named(%s, %s)", name, ruleSource.getName());
        ModelReference<T> subject = ModelReference.of(modelNode.getPath().child(name), elementType);
        modelNode.defineRulesForLink(ModelActionRole.Defaults, new FilteringActionWrapper<T>(elementFilter, subject, DirectNodeNoInputsModelAction.of(subject, descriptor, new ApplyRuleSource(ruleSource))));
    }

    // Called from transformed DSL rules
    public void named(String name, final DeferredModelAction action) {
        viewState.assertCanMutate();
        ModelReference<T> subject = ModelReference.of(modelNode.getPath().child(name), elementType);
        modelNode.applyToLink(ModelActionRole.Initialize, new FilteringActionWrapper<T>(elementFilter, subject, new DeferredActionWrapper<T>(subject, ModelActionRole.Mutate, action)));
    }

    @Override
    public String getDisplayName() {
        return publicType.getDisplayName() + " '" + modelNode.getPath() + "'";
    }

    @Override
    public Collection<T> values() {
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
        mutateChildren(ModelActionRole.Mutate, ModelType.of(type), "withType()", configAction);
    }

    // Called from transformed DSL rules
    public <S> void withType(Class<S> type, DeferredModelAction configAction) {
        mutateChildren(ModelActionRole.Mutate, ModelType.of(type), configAction);
    }

    @Override
    public <S> void withType(Class<S> type, Class<? extends RuleSource> rules) {
        viewState.assertCanMutate();
        modelNode.applyTo(allLinks(elementFilter.withType(type)), rules);
    }

    @Override
    public <S> ModelMap<S> withType(Class<S> typeClass) {
        ModelType<S> type = ModelType.of(typeClass);
        return withType(type);
    }

    public <S> ModelMap<S> withType(ModelType<S> type) {
        if (type.equals(elementType)) {
            return uncheckedCast(this);
        }

        ChildNodeInitializerStrategy<S> creatorStrategy1 = uncheckedCast(this.creatorStrategy);
        return new NodeBackedModelMap<S>(publicType, type, sourceDescriptor, modelNode, viewState, elementFilter, creatorStrategy1);
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

    private static abstract class ElementFilter implements Predicate<MutableModelNode>, Spec<ModelType<?>> {
        protected final ModelType<?> elementType;

        public ElementFilter(ModelType<?> elementType) {
            this.elementType = elementType;
        }

        public ElementFilter withType(Class<?> elementType) {
            return withType(ModelType.of(elementType));
        }

        public ElementFilter withType(ModelType<?> elementType) {
            if (this.elementType.equals(elementType)) {
                return this;
            } else {
                return new ChainedElementFilter(this, elementType);
            }
        }

        public abstract void validateCanBindAction(MutableModelNode node, ModelAction action);

        public abstract void validateCanCreateElement(ModelPath path, ModelType<?> type);
    }

    private static class ChainedElementFilter extends ElementFilter {
        private final ElementFilter parent;

        public ChainedElementFilter(ElementFilter parent, ModelType<?> elementType) {
            super(elementType);
            this.parent = parent;
        }

        @Override
        public boolean isSatisfiedBy(ModelType<?> element) {
            return elementType.isAssignableFrom(element) && parent.isSatisfiedBy(element);
        }

        @Override
        public boolean apply(MutableModelNode node) {
            node.ensureAtLeast(ModelNode.State.Discovered);
            return node.canBeViewedAs(elementType) && parent.apply(node);
        }

        @Override
        public void validateCanBindAction(MutableModelNode node, ModelAction action) {
            node.ensureAtLeast(ModelNode.State.Discovered);
            if (!node.canBeViewedAs(elementType)) {
                throw new InvalidModelRuleException(action.getDescriptor(), new ModelRuleBindingException(
                    IncompatibleTypeReferenceReporter.of(node, elementType, action.getSubject().getDescription(), true).asString()
                ));
            }
            parent.validateCanBindAction(node, action);
        }

        @Override
        public void validateCanCreateElement(ModelPath path, ModelType<?> type) {
            if (!elementType.isAssignableFrom(type)) {
                throw new IllegalArgumentException(String.format("Cannot create '%s' with type '%s' as this is not a subtype of '%s'.", path, type, elementType));
            }
            parent.validateCanCreateElement(path, type);
        }
    }

    private static class DeferredActionWrapper<T> extends AbstractModelAction<T> {
        private final ModelActionRole role;
        private final DeferredModelAction action;

        public DeferredActionWrapper(ModelReference<T> subject, ModelActionRole role, DeferredModelAction action) {
            super(subject, action.getDescriptor(), Collections.<ModelReference<?>>emptyList());
            this.role = role;
            this.action = action;
        }

        @Override
        public void execute(MutableModelNode node, List<ModelView<?>> inputs) {
            action.execute(node, role);
        }
    }

    private static class FilteringActionWrapper<T> extends AbstractModelAction<T> {

        private final ElementFilter elementFilter;
        private final ModelAction delegate;

        public FilteringActionWrapper(ElementFilter elementFilter, ModelReference<T> subject, ModelAction delegate) {
            super(subject, delegate.getDescriptor(), delegate.getInputs());
            this.elementFilter = elementFilter;
            this.delegate = delegate;
        }

        @Override
        public void execute(MutableModelNode modelNode, List<ModelView<?>> inputs) {
            elementFilter.validateCanBindAction(modelNode, delegate);
            delegate.execute(modelNode, inputs);
        }
    }

    private static class ApplyRuleSource implements Action<MutableModelNode> {
        private final ModelType<? extends RuleSource> rules;

        public ApplyRuleSource(Class<? extends RuleSource> rules) {
            this.rules = ModelType.of(rules);
        }

        @Override
        public void execute(MutableModelNode node) {
            node.applyToSelf(rules.getConcreteClass());
        }
    }
}
