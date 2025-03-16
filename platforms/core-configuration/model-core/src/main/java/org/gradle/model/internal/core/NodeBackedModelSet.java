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
import com.google.common.collect.Lists;
import groovy.lang.Closure;
import org.gradle.api.Action;
import org.gradle.api.specs.Specs;
import org.gradle.model.ModelSet;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;
import org.gradle.model.internal.manage.instance.ManagedInstance;
import org.gradle.model.internal.type.ModelType;
import org.gradle.util.internal.ClosureBackedAction;

import java.util.Collection;
import java.util.Iterator;

import static org.gradle.model.internal.core.NodePredicate.allLinks;

public class NodeBackedModelSet<T> implements ModelSet<T>, ManagedInstance {

    private final ModelType<T> elementType;
    private final ModelRuleDescriptor descriptor;
    private final MutableModelNode modelNode;
    private final ModelViewState state;
    private final ChildNodeInitializerStrategy<T> creatorStrategy;
    private final ModelReference<T> elementTypeReference;
    private final ModelType<?> publicType;

    private Collection<T> elements;

    public NodeBackedModelSet(ModelType<?> publicType, ModelType<T> elementType, ModelRuleDescriptor descriptor, MutableModelNode modelNode, ModelViewState state, ChildNodeInitializerStrategy<T> creatorStrategy) {
        this.publicType = publicType;
        this.elementType = elementType;
        this.elementTypeReference = ModelReference.of(elementType);
        this.descriptor = descriptor;
        this.modelNode = modelNode;
        this.state = state;
        this.creatorStrategy = creatorStrategy;
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
    public String getName() {
        return modelNode.getPath().getName();
    }

    @Override
    public String getDisplayName() {
        return publicType.getDisplayName() + " '" + modelNode.getPath() + "'";
    }

    @Override
    public String toString() {
        return getDisplayName();
    }

    @Override
    public void create(final Action<? super T> action) {
        state.assertCanMutate();

        String name = String.valueOf(modelNode.getLinkCount(ModelNodes.withType(elementType)));
        ModelPath childPath = modelNode.getPath().child(name);
        final ModelRuleDescriptor descriptor = this.descriptor.append("create()");

        NodeInitializer nodeInitializer = creatorStrategy.initializer(elementType, Specs.<ModelType<?>>satisfyAll());
        ModelRegistration registration = ModelRegistrations.of(childPath, nodeInitializer)
            .descriptor(descriptor)
            .action(ModelActionRole.Initialize, NoInputsModelAction.of(ModelReference.of(childPath, elementType), descriptor, action))
            .build();

        modelNode.addLink(registration);
    }

    @Override
    public void afterEach(Action<? super T> configAction) {
        state.assertCanMutate();
        modelNode.applyTo(allLinks(), ModelActionRole.Finalize, NoInputsModelAction.of(elementTypeReference, descriptor.append("afterEach()"), configAction));
    }

    @Override
    public void beforeEach(Action<? super T> configAction) {
        state.assertCanMutate();
        modelNode.applyTo(allLinks(), ModelActionRole.Defaults, NoInputsModelAction.of(elementTypeReference, descriptor.append("afterEach()"), configAction));
    }

    @Override
    public int size() {
        state.assertCanReadChildren();
        return modelNode.getLinkCount(ModelNodes.withType(elementType));
    }

    @Override
    public boolean isEmpty() {
        return size() == 0;
    }

    @Override
    public boolean contains(Object o) {
        return getElements().contains(o);
    }

    @Override
    public Iterator<T> iterator() {
        return getElements().iterator();
    }

    @Override
    public Object[] toArray() {
        return getElements().toArray();
    }

    @Override
    public <E> E[] toArray(E[] a) {
        return getElements().toArray(a);
    }

    @Override
    public boolean add(T e) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean remove(Object o) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        return getElements().containsAll(c);
    }

    @Override
    public boolean addAll(Collection<? extends T> c) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void clear() {
        throw new UnsupportedOperationException();
    }

    // TODO - mix this in using decoration. Also validate closure parameter types, if declared
    public void create(Closure<?> closure) {
        create(ClosureBackedAction.of(closure));
    }

    public void afterEach(Closure<?> closure) {
        afterEach(ClosureBackedAction.of(closure));
    }

    public void beforeEach(Closure<?> closure) {
        beforeEach(ClosureBackedAction.of(closure));
    }

    private Collection<T> getElements() {
        state.assertCanReadChildren();
        if (elements == null) {
            elements = Lists.newArrayList(
                Iterables.transform(modelNode.getLinks(ModelNodes.withType(elementType)), new Function<MutableModelNode, T>() {
                    @Override
                    public T apply(MutableModelNode input) {
                        return input.asImmutable(elementType, descriptor).getInstance();
                    }
                })
            );
        }
        return elements;
    }

}
