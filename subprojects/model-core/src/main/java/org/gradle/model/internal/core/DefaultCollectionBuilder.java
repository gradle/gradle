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
import org.gradle.internal.Actions;
import org.gradle.internal.ErroringAction;
import org.gradle.internal.Factory;
import org.gradle.model.collection.CollectionBuilder;
import org.gradle.model.internal.core.rule.describe.ActionModelRuleDescriptor;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;
import org.gradle.model.internal.core.rule.describe.NestedModelRuleDescriptor;
import org.gradle.model.internal.type.ModelType;

import java.util.Collections;
import java.util.Set;

@NotThreadSafe
public class DefaultCollectionBuilder<T> implements CollectionBuilder<T> {

    private final NamedEntityInstantiator<T> instantiator;
    private final ModelRuleDescriptor sourceDescriptor;
    private final ModelNode modelNode;

    public DefaultCollectionBuilder(NamedEntityInstantiator<T> instantiator, ModelRuleDescriptor sourceDescriptor, ModelNode modelNode) {
        this.instantiator = instantiator;
        this.sourceDescriptor = sourceDescriptor;
        this.modelNode = modelNode;
    }

    public void create(final String name) {
        doCreate(name, instantiator.getType(), Actions.doNothing(), new DefaultTypeFactory(name));
    }

    public void create(String name, Action<? super T> configAction) {
        doCreate(name, instantiator.getType(), configAction, new DefaultTypeFactory(name));
    }

    public <S extends T> void create(String name, Class<S> type) {
        doCreate(name, ModelType.of(type), Actions.<S>doNothing(), new CustomTypeFactory<S>(name, type));
    }

    public <S extends T> void create(final String name, final Class<S> type, Action<? super S> configAction) {
        doCreate(name, ModelType.of(type), configAction, new CustomTypeFactory<S>(name, type));
    }

    private <S extends T> void doCreate(final String name, ModelType<S> type, Action<? super S> configAction, Factory<? extends S> factory) {
        ModelRuleDescriptor descriptor = new NestedModelRuleDescriptor(sourceDescriptor, ActionModelRuleDescriptor.from(new ErroringAction<Appendable>() {
            @Override
            protected void doExecute(Appendable thing) throws Exception {
                thing.append("create(").append(name).append(")");
            }
        }));

        // TODO reuse pooled projections/promises/adapters
        Set<ModelProjection> projections = Collections.<ModelProjection>singleton(new UnmanagedModelProjection<S>(type, true, true));
        ModelPromise promise = new ProjectionBackedModelPromise(projections);
        ModelAdapter adapter = new ProjectionBackedModelAdapter(projections);

        ModelNode childNode = modelNode.addLink(name, descriptor, promise, adapter);

        S s = factory.create();
        configAction.execute(s);
        childNode.setPrivateData(type, s);
    }

    private class CustomTypeFactory<S extends T> implements Factory<S> {
        private final String name;
        private final Class<S> type;

        public CustomTypeFactory(String name, Class<S> type) {
            this.name = name;
            this.type = type;
        }

        public S create() {
            return instantiator.create(name, type);
        }
    }

    private class DefaultTypeFactory implements Factory<T> {
        private final String name;

        public DefaultTypeFactory(String name) {
            this.name = name;
        }

        public T create() {
            return instantiator.create(name);
        }
    }

}
