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

package org.gradle.model.collection.internal;

import org.gradle.api.Action;
import org.gradle.internal.Actions;
import org.gradle.internal.ErroringAction;
import org.gradle.internal.Factory;
import org.gradle.model.collection.CollectionBuilder;
import org.gradle.model.entity.internal.NamedEntityInstantiator;
import org.gradle.model.internal.core.*;
import org.gradle.model.internal.core.rule.describe.ActionModelRuleDescriptor;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;
import org.gradle.model.internal.core.rule.describe.NestedModelRuleDescriptor;

public class DefaultCollectionBuilder<T> implements CollectionBuilder<T> {

    private final ModelPath collectionPath;
    private final NamedEntityInstantiator<T> instantiator;
    private final ModelRuleDescriptor sourceDescriptor;
    private final Inputs implicitInputs;
    private final ModelRuleRegistrar ruleRegistrar;

    public DefaultCollectionBuilder(ModelPath collectionPath, NamedEntityInstantiator<T> instantiator, ModelRuleDescriptor sourceDescriptor, Inputs implicitInputs, ModelRuleRegistrar ruleRegistrar) {
        this.collectionPath = collectionPath;
        this.instantiator = instantiator;
        this.sourceDescriptor = sourceDescriptor;
        this.implicitInputs = implicitInputs;
        this.ruleRegistrar = ruleRegistrar;
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
        ModelReference<S> modelReference = ModelReference.of(collectionPath.child(name), type);
        ModelRuleDescriptor descriptor = new NestedModelRuleDescriptor(sourceDescriptor, ActionModelRuleDescriptor.from(new ErroringAction<Appendable>() {
            @Override
            protected void doExecute(Appendable thing) throws Exception {
                thing.append("create(").append(name).append(")");
            }
        }));

        ruleRegistrar.create(
                ModelCreators.of(modelReference, new CreateAndConfigureFactory<S>(factory, configAction))
                        .descriptor(descriptor)
                        .inputs(implicitInputs.getReferences())
                        .build()
        );
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

    private static class CreateAndConfigureFactory<T> implements Factory<T> {

        private final Factory<? extends T> factory;
        private final Action<? super T> action;

        private CreateAndConfigureFactory(Factory<? extends T> factory, Action<? super T> action) {
            this.factory = factory;
            this.action = action;
        }

        public T create() {
            T t = factory.create();
            action.execute(t);
            return t;
        }
    }
}
