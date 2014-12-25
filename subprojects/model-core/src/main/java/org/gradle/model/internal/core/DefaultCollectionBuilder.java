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
                S element = instantiator.create(name, type);
                target.add(element);
                return element;
            }
        });
    }

    @Override
    public <S extends T> void create(final String name, final Class<S> type, final Action<? super S> configAction) {
        doCreate(name, ModelType.of(type), new Factory<S>() {
            @Override
            public S create() {
                S element = instantiator.create(name, type);
                configAction.execute(element);
                target.add(element);
                return element;
            }
        });
    }

    private <S extends T> void doCreate(final String name, ModelType<S> type, Factory<? extends S> factory) {
        ModelRuleDescriptor descriptor = new NestedModelRuleDescriptor(sourceDescriptor, ActionModelRuleDescriptor.from(new ErroringAction<Appendable>() {
            @Override
            protected void doExecute(Appendable thing) throws Exception {
                thing.append("create(").append(name).append(")");
            }
        }));

        ModelReference<S> subject = ModelReference.of(modelNode.getPath().child(name), type);
        modelNode.addLink(ModelCreators.unmanagedInstance(subject, factory).descriptor(descriptor).build());
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
        ModelRuleDescriptor descriptor = new NestedModelRuleDescriptor(sourceDescriptor, ActionModelRuleDescriptor.from(new ErroringAction<Appendable>() {
            @Override
            protected void doExecute(Appendable thing) throws Exception {
                thing.append("all()");
            }
        }));
        ModelReference<T> subject = ModelReference.of(elementType);
        modelNode.mutateAllLinks(MutationType.Mutate, new ActionBackedMutateRule<T>(subject, configAction, descriptor));
    }

    @Override
    public void finalizeAll(Action<? super T> configAction) {
        ModelRuleDescriptor descriptor = new NestedModelRuleDescriptor(sourceDescriptor, ActionModelRuleDescriptor.from(new ErroringAction<Appendable>() {
            @Override
            protected void doExecute(Appendable thing) throws Exception {
                thing.append("all()");
            }
        }));
        ModelReference<T> subject = ModelReference.of(elementType);
        modelNode.mutateAllLinks(MutationType.Finalize, new ActionBackedMutateRule<T>(subject, configAction, descriptor));
    }
}
