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

package org.gradle.model.collection.internal;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.Iterables;
import org.gradle.api.Action;
import org.gradle.api.Named;
import org.gradle.api.Namer;
import org.gradle.api.Transformer;
import org.gradle.api.internal.PolymorphicDomainObjectContainerInternal;
import org.gradle.internal.BiAction;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.model.collection.CollectionBuilder;
import org.gradle.model.internal.core.*;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;
import org.gradle.model.internal.type.ModelType;
import org.gradle.util.CollectionUtils;

import java.util.Collection;
import java.util.Collections;

public class PolymorphicDomainObjectContainerModelProjection<C extends PolymorphicDomainObjectContainerInternal<M>, M> implements ModelProjection {

    private final Instantiator instantiator;
    private final C container;
    private final Class<M> itemType;

    public PolymorphicDomainObjectContainerModelProjection(Instantiator instantiator, C container, Class<M> itemType) {
        this.instantiator = instantiator;
        this.container = container;
        this.itemType = itemType;
    }

    public <T> boolean canBeViewedAsWritable(ModelType<T> targetType) {
        if (targetType.getRawClass().equals(CollectionBuilder.class)) {
            ModelType<?> targetItemType = targetType.getTypeVariables().get(0);
            return targetItemType.getRawClass().isAssignableFrom(itemType) || itemType.isAssignableFrom(targetItemType.getRawClass());
        } else {
            return false;
        }
    }

    public <T> boolean canBeViewedAsReadOnly(ModelType<T> type) {
        return false;
    }

    public <T> ModelView<? extends T> asWritable(ModelType<T> targetType, MutableModelNode node, ModelRuleDescriptor ruleDescriptor, Inputs inputs) {
        if (canBeViewedAsWritable(targetType)) {
            ModelType<?> targetItemType = targetType.getTypeVariables().get(0);
            if (targetItemType.getRawClass().isAssignableFrom(itemType)) { // item type is super of base
                return toView(ruleDescriptor, node, itemType, container);
            } else { // item type is sub type
                Class<? extends M> subType = targetItemType.getRawClass().asSubclass(itemType);
                return toView(ruleDescriptor, node, subType, container);
            }
        } else {
            return null;
        }
    }

    private <T, S extends M> ModelView<? extends T> toView(ModelRuleDescriptor sourceDescriptor, MutableModelNode node, Class<S> itemType, C container) {
        CollectionBuilder<S> builder = new DefaultCollectionBuilder<S>(itemType, container.getEntityInstantiator(), container, sourceDescriptor, node);
        ModelType<CollectionBuilder<S>> viewType = new ModelType.Builder<CollectionBuilder<S>>() {
        }.where(new ModelType.Parameter<S>() {
        }, ModelType.of(itemType)).build();
        CollectionBuilderModelView<S> view = new CollectionBuilderModelView<S>(instantiator, viewType, builder, sourceDescriptor);
        @SuppressWarnings("unchecked") ModelView<T> cast = (ModelView<T>) view;
        return cast;
    }

    public <T> ModelView<? extends T> asReadOnly(ModelType<T> type, MutableModelNode modelNode, ModelRuleDescriptor ruleDescriptor) {
        return null;
    }

    public Iterable<String> getWritableTypeDescriptions() {
        return Collections.singleton(getBuilderTypeDescriptionForCreatableTypes(container.getCreateableTypes()));
    }

    public Iterable<String> getReadableTypeDescriptions() {
        return Collections.emptySet();
    }

    public static String getBuilderTypeDescriptionForCreatableTypes(Collection<? extends Class<?>> createableTypes) {
        StringBuilder sb = new StringBuilder(CollectionBuilder.class.getName());
        if (createableTypes.size() == 1) {
            @SuppressWarnings("ConstantConditions")
            String onlyType = Iterables.getFirst(createableTypes, null).getName();
            sb.append("<").append(onlyType).append(">");
        } else {
            sb.append("<T>; where T is one of [");
            Joiner.on(", ").appendTo(sb, CollectionUtils.sort(Iterables.transform(createableTypes, new Function<Class<?>, String>() {
                public String apply(Class<?> input) {
                    return input.getName();
                }
            })));
            sb.append("]");
        }
        return sb.toString();
    }

    public static <I extends Named, C extends PolymorphicDomainObjectContainerInternal<I>, P /* super C */> ModelCreator bridgeNamedDomainObjectCollection(
            Instantiator instantiator,
            final ModelType<C> containerType,
            final ModelType<P> publicType,
            final ModelType<I> itemType,
            final ModelPath modelPath,
            final C container,
            final String descriptor
    ) {
        return bridgeNamedDomainObjectCollection(
                instantiator,
                containerType,
                publicType,
                itemType,
                modelPath,
                container,
                new Named.Namer(),
                descriptor,
                new Transformer<String, String>() {
                    @Override
                    public String transform(String s) {
                        return descriptor + '.' + s + "()";
                    }
                }
        );
    }

    public static <I, C extends PolymorphicDomainObjectContainerInternal<I>, P /* super C */> ModelCreator bridgeNamedDomainObjectCollection(
            Instantiator instantiator,
            final ModelType<C> containerType,
            final ModelType<P> publicType,
            final ModelType<I> itemType,
            final ModelPath modelPath,
            final C container,
            final Namer<? super I> namer,
            final String descriptor,
            final Transformer<String, String> itemDescriptorGenerator
    ) {
        return ModelCreators.of(
                ModelReference.of(modelPath, containerType),
                new BiAction<MutableModelNode, Inputs>() {
                    public void execute(final MutableModelNode modelNode, Inputs inputs) {
                        modelNode.setPrivateData(containerType, container);
                        container.all(new Action<I>() {
                            public void execute(final I item) {
                                final String name = namer.determineName(item);

                                if (!modelNode.hasLink(name)) {
                                    ModelType<I> itemType = ModelType.typeOf(item);
                                    ModelReference<I> itemReference = ModelReference.of(modelNode.getPath().child(name), itemType);
                                    modelNode.addLink(
                                            ModelCreators.bridgedInstance(itemReference, item)
                                                    .simpleDescriptor(itemDescriptorGenerator.transform(name)).build());
                                }
                            }
                        });
                        container.whenObjectRemoved(new Action<I>() {
                            public void execute(I item) {
                                modelNode.removeLink(namer.determineName(item));
                            }
                        });
                    }
                }
        )
                .simpleDescriptor(descriptor)
                .withProjection(new UnmanagedModelProjection<P>(publicType, true, true))
                .withProjection(new PolymorphicDomainObjectContainerModelProjection<C, I>(instantiator, container, itemType.getConcreteClass()))
                .build();
    }
}
