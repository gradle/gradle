/*
 * Copyright 2013 the original author or authors.
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

import net.jcip.annotations.NotThreadSafe;
import org.gradle.api.Action;
import org.gradle.api.Nullable;
import org.gradle.internal.ErroringAction;
import org.gradle.internal.Factory;
import org.gradle.model.collection.CollectionBuilder;
import org.gradle.model.internal.core.rule.describe.ActionModelRuleDescriptor;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;
import org.gradle.model.internal.core.rule.describe.NestedModelRuleDescriptor;
import org.gradle.model.internal.type.ModelType;

import java.util.Collection;

@NotThreadSafe
public class DefaultCollectionBuilder<T> implements CollectionBuilder<T> {
    private final Class<T> elementType;
    private final NamedEntityInstantiator<? super T> instantiator;
    private final Collection<? super T> target;
    private final ModelRuleDescriptor sourceDescriptor;
    private final MutableModelNode modelNode;

    public DefaultCollectionBuilder(Class<T> elementType, NamedEntityInstantiator<? super T> instantiator, Collection<? super T> target, ModelRuleDescriptor sourceDescriptor, MutableModelNode modelNode) {
        this.elementType = elementType;
        this.instantiator = instantiator;
        this.target = target;
        this.sourceDescriptor = sourceDescriptor;
        this.modelNode = modelNode;
    }

    @Override
    public String toString() {
        return target.toString();
    }

    @Nullable
    @Override
    public T get(String name) {
        // TODO - lock this down
        MutableModelNode link = modelNode.getLink(name);
        return link == null ? null : link.getPrivateData(ModelType.of(elementType));
    }

    @Override
    public void create(final String name) {
        create(name, elementType);
    }

    @Override
    public void create(String name, Action<? super T> configAction) {
        create(name, elementType, configAction);
    }

    @Override
    public <S extends T> void create(final String name, final Class<S> type) {
        doCreate(name, ModelType.of(type), new Factory<S>() {
            @Override
            public S create() {
                return instantiator.create(name, type);
            }
        }, new Action<S>() {
            @Override
            public void execute(S s) {
                target.add(s);
            }
        });
    }

    @Override
    public <S extends T> void create(final String name, final Class<S> type, final Action<? super S> configAction) {
        doCreate(name, ModelType.of(type), new Factory<S>() {
            @Override
            public S create() {
                return instantiator.create(name, type);
            }
        }, new Action<S>() {
            @Override
            public void execute(S s) {
                configAction.execute(s);
                target.add(s);
            }
        });
    }

    private <S extends T> void doCreate(final String name, ModelType<S> type, Factory<? extends S> factory, Action<? super S> initAction) {
        ModelRuleDescriptor descriptor = new NestedModelRuleDescriptor(sourceDescriptor, ActionModelRuleDescriptor.from(new ErroringAction<Appendable>() {
            @Override
            protected void doExecute(Appendable thing) throws Exception {
                thing.append("create(").append(name).append(")");
            }
        }));

        ModelReference<S> subject = ModelReference.of(modelNode.getPath().child(name), type);
        modelNode.addLink(ModelCreators.unmanagedInstance(subject, factory).descriptor(descriptor).build());
        modelNode.mutateLink(MutationType.Initialize, new ActionBackedMutateRule<S>(subject, initAction, descriptor));
    }

    @Override
    public void named(final String name, Action<? super T> configAction) {
        ModelRuleDescriptor descriptor = new NestedModelRuleDescriptor(sourceDescriptor, ActionModelRuleDescriptor.from(new ErroringAction<Appendable>() {
            @Override
            protected void doExecute(Appendable thing) throws Exception {
                thing.append("named(").append(name).append(")");
            }
        }));
        ModelReference<T> subject = ModelReference.of(modelNode.getPath().child(name), elementType);
        modelNode.mutateLink(MutationType.Mutate, new ActionBackedMutateRule<T>(subject, configAction, descriptor));
    }

    @Override
    public void all(final Action<? super T> configAction) {
        withType(elementType, configAction);
    }

    @Override
    public <S extends T> void withType(Class<S> type, Action<? super S> configAction) {
        ModelRuleDescriptor descriptor = new NestedModelRuleDescriptor(sourceDescriptor, ActionModelRuleDescriptor.from(new ErroringAction<Appendable>() {
            @Override
            protected void doExecute(Appendable thing) throws Exception {
                thing.append("all()");
            }
        }));
        ModelReference<S> subject = ModelReference.of(type);
        modelNode.mutateAllLinks(MutationType.Mutate, new ActionBackedMutateRule<S>(subject, configAction, descriptor));
    }

    @Override
    public void beforeEach(Action<? super T> configAction) {
        beforeEach(elementType, configAction);
    }

    @Override
    public <S extends T> void beforeEach(Class<S> type, Action<? super S> configAction) {
        ModelRuleDescriptor descriptor = new NestedModelRuleDescriptor(sourceDescriptor, ActionModelRuleDescriptor.from(new ErroringAction<Appendable>() {
            @Override
            protected void doExecute(Appendable thing) throws Exception {
                thing.append("beforeEach()");
            }
        }));
        ModelReference<S> subject = ModelReference.of(type);
        modelNode.mutateAllLinks(MutationType.Defaults, new ActionBackedMutateRule<S>(subject, configAction, descriptor));
    }

    @Override
    public void finalizeAll(Action<? super T> configAction) {
        finalizeAll(elementType, configAction);
    }

    @Override
    public <S extends T> void finalizeAll(Class<S> type, Action<? super S> configAction) {
        ModelRuleDescriptor descriptor = new NestedModelRuleDescriptor(sourceDescriptor, ActionModelRuleDescriptor.from(new ErroringAction<Appendable>() {
            @Override
            protected void doExecute(Appendable thing) throws Exception {
                thing.append("finalizeAll()");
            }
        }));
        ModelReference<S> subject = ModelReference.of(type);
        modelNode.mutateAllLinks(MutationType.Finalize, new ActionBackedMutateRule<S>(subject, configAction, descriptor));
    }
}
