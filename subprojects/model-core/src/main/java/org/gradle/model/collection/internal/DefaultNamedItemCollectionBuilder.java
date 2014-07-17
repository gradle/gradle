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
import org.gradle.internal.Factory;
import org.gradle.model.collection.NamedItemCollectionBuilder;
import org.gradle.model.entity.internal.NamedEntityInstantiator;
import org.gradle.model.internal.core.*;
import org.gradle.model.internal.core.rule.describe.ModelRuleSourceDescriptor;
import org.gradle.model.internal.core.rule.describe.NestedModelRuleSourceDescriptor;
import org.gradle.model.internal.core.rule.describe.SimpleModelRuleSourceDescriptor;

public class DefaultNamedItemCollectionBuilder<T> implements NamedItemCollectionBuilder<T> {

    private final ModelPath collectionPath;
    private final NamedEntityInstantiator<T> instantiator;
    private final ModelRuleSourceDescriptor sourceDescriptor;
    private final Inputs implicitInputs;
    private final ModelRuleRegistrar ruleRegistrar;

    public DefaultNamedItemCollectionBuilder(ModelPath collectionPath, NamedEntityInstantiator<T> instantiator, ModelRuleSourceDescriptor sourceDescriptor, Inputs implicitInputs, ModelRuleRegistrar ruleRegistrar) {
        this.collectionPath = collectionPath;
        this.instantiator = instantiator;
        this.sourceDescriptor = sourceDescriptor;
        this.implicitInputs = implicitInputs;
        this.ruleRegistrar = ruleRegistrar;
    }

    public void create(String name) {
        create(name, (Action<? super T>) null);
    }

    public void create(final String name, Action<? super T> configAction) {
        doCreate(name, instantiator.getType(), configAction, new Factory<T>() {
            public T create() {
                return instantiator.create(name);
            }
        });
    }

    private <S extends T> void doCreate(final String name, ModelType<S> type, Action<? super S> configAction, Factory<S> factory) {
        ModelPath path = collectionPath.child(name);
        ModelRuleSourceDescriptor descriptor = new NestedModelRuleSourceDescriptor(sourceDescriptor, new SimpleModelRuleSourceDescriptor(name));

        ruleRegistrar.create(InstanceBackedModelCreator.of(
                ModelReference.of(path, type),
                descriptor,
                implicitInputs.getReferences(),
                factory
        ));

        if (configAction != null) {
            ruleRegistrar.mutate(
                    ActionBackedModelMutator.of(
                            ModelReference.of(path, type),
                            implicitInputs.getReferences(),
                            new NestedModelRuleSourceDescriptor(descriptor, new SimpleModelRuleSourceDescriptor("configure")),
                            configAction
                    )
            );
        }
    }

    public void create(String name, Class<? extends T> type) {
        create(name, type, null);
    }

    public <S extends T> void create(final String name, final Class<S> type, Action<? super S> configAction) {
        doCreate(name, ModelType.of(type), configAction, new Factory<S>() {
            public S create() {
                return instantiator.create(name, type);
            }
        });
    }

}
