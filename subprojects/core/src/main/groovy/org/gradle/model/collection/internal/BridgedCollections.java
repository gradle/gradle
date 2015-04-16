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

import org.gradle.api.Action;
import org.gradle.api.Namer;
import org.gradle.api.Transformer;
import org.gradle.api.internal.PolymorphicDomainObjectContainerInternal;
import org.gradle.internal.BiAction;
import org.gradle.internal.Transformers;
import org.gradle.model.internal.core.*;
import org.gradle.model.internal.registry.ModelRegistry;
import org.gradle.model.internal.type.ModelType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.List;

public abstract class BridgedCollections {

    private static final Logger LOGGER = LoggerFactory.getLogger(BridgedCollections.class);

    private BridgedCollections() {
    }

    public static <I> ModelReference<NamedEntityInstantiator<I>> instantiatorReference(ModelPath containerPath, ModelType<I> itemType) {
        final String instantiatorNodeName = "__instantiator";
        return ModelReference.of(
                containerPath.child(instantiatorNodeName),
                new ModelType.Builder<NamedEntityInstantiator<I>>() {
                }.where(new ModelType.Parameter<I>() {
                }, itemType).build()
        );
    }

    private static class ContainerInfo<I> {
        final ModelCreators.Builder creatorBuilder;
        final ModelReference<NamedEntityInstantiator<I>> instantiatorReference;
        final ModelReference<? extends Collection<I>> storeReference;

        public ContainerInfo(ModelCreators.Builder creatorBuilder, ModelReference<NamedEntityInstantiator<I>> instantiatorReference, ModelReference<? extends Collection<I>> storeReference) {
            this.creatorBuilder = creatorBuilder;
            this.instantiatorReference = instantiatorReference;
            this.storeReference = storeReference;
        }
    }

    private static <I, C extends PolymorphicDomainObjectContainerInternal<I>> ContainerInfo<I> creator(
            final ModelRegistry modelRegistry,
            final ModelReference<C> containerReference,
            final ModelType<I> itemType,
            final Transformer<? extends C, ? super MutableModelNode> containerFactory,
            final Namer<? super I> namer,
            String descriptor,
            final Transformer<String, String> itemDescriptorGenerator
    ) {
        assert containerReference.getPath() != null : "container reference path cannot be null";

        final String storeNodeName = "__store";

        final ModelReference<NamedEntityInstantiator<I>> instantiatorReference = instantiatorReference(containerReference.getPath(), itemType);

        final ModelReference<C> storeReference = ModelReference.of(
                containerReference.getPath().child(storeNodeName),
                containerReference.getType()
        );

        ModelCreators.Builder creatorBuilder = ModelCreators.of(
                containerReference,
                new BiAction<MutableModelNode, List<ModelView<?>>>() {
                    public void execute(final MutableModelNode containerNode, List<ModelView<?>> inputs) {

                        C container = containerFactory.transform(containerNode);

                        ModelCreator storeCreator = ModelCreators.bridgedInstance(storeReference, container)
                                .ephemeral(true)
                                .hidden(true)
                                .descriptor(itemDescriptorGenerator.transform(storeNodeName))
                                .build();

                        modelRegistry.createOrReplace(storeCreator);

                        @SuppressWarnings("ConstantConditions")
                        String instantiatorNodeName = instantiatorReference.getPath().getName();

                        ModelCreator instantiatorCreator = ModelCreators.bridgedInstance(instantiatorReference, container.getEntityInstantiator())
                                .ephemeral(true)
                                .hidden(true)
                                .descriptor(itemDescriptorGenerator.transform(instantiatorNodeName))
                                .build();

                        modelRegistry.createOrReplace(instantiatorCreator);

                        containerNode.setPrivateData(containerReference.getType(), container);
                        container.all(new Action<I>() {
                            public void execute(final I item) {
                                final String name = namer.determineName(item);

                                // For now, ignore elements added after the container has been closed
                                if (!containerNode.isMutable()) {
                                    LOGGER.debug("Ignoring element '{}' added to '{}' after it is closed.", containerReference.getPath(), name);
                                    return;
                                }

                                if (!containerNode.hasLink(name)) {
                                    ModelType<I> itemType = ModelType.typeOf(item);
                                    ModelReference<I> itemReference = ModelReference.of(containerReference.getPath().child(name), itemType);
                                    ModelCreator itemCreator = ModelCreators.of(itemReference, new BiAction<MutableModelNode, List<ModelView<?>>>() {
                                        @Override
                                        public void execute(MutableModelNode modelNode, List<ModelView<?>> modelViews) {
                                            C container = ModelViews.assertType(modelViews.get(0), storeReference.getType()).getInstance();
                                            I item = container.getByName(name);
                                            modelNode.setPrivateData(ModelType.typeOf(item), item);
                                        }
                                    })
                                            .inputs(storeReference)
                                            .withProjection(new UnmanagedModelProjection<I>(itemType, true, true))
                                            .descriptor(itemDescriptorGenerator.transform(name)).build();

                                    containerNode.addLink(itemCreator);
                                }
                            }
                        });
                        container.whenObjectRemoved(new Action<I>() {
                            public void execute(I item) {
                                String name = namer.determineName(item);
                                containerNode.removeLink(name);
                            }
                        });
                    }
                }
        )
                .ephemeral(true)
                .descriptor(descriptor);

        return new ContainerInfo<I>(creatorBuilder, instantiatorReference, storeReference);
    }

    public static <I, C extends PolymorphicDomainObjectContainerInternal<I>, P /* super C */> void dynamicTypes(
            ModelRegistry modelRegistry,
            ModelPath modelPath,
            String descriptor,
            ModelType<P> publicType,
            ModelType<C> containerType,
            ModelType<I> itemType,
            C container,
            Namer<? super I> namer,
            Transformer<String, String> itemDescriptorGenerator
    ) {
        ModelReference<C> containerReference = ModelReference.of(modelPath, containerType);

        ContainerInfo<I> containerInfo = creator(modelRegistry, containerReference, itemType, Transformers.constant(container), namer, descriptor, itemDescriptorGenerator);

        modelRegistry.createOrReplace(containerInfo.creatorBuilder
                .withProjection(new DynamicTypesDomainObjectContainerModelProjection<C, I>(container, itemType, containerInfo.instantiatorReference, containerInfo.storeReference))
                .withProjection(new UnmanagedModelProjection<P>(publicType, true, true))
                .build());
    }

    public static <I, C extends PolymorphicDomainObjectContainerInternal<I>, P /* super C */> void staticTypes(
            ModelRegistry modelRegistry,
            ModelPath modelPath,
            ModelType<C> containerType,
            ModelType<I> itemType,
            ModelType<P> publicType,
            Transformer<? extends C, ? super MutableModelNode> containerFactory,
            Namer<? super I> namer,
            String descriptor,
            Transformer<String, String> itemDescriptorGenerator
    ) {
        ModelReference<C> containerReference = ModelReference.of(modelPath, containerType);

        ContainerInfo<I> containerInfo = creator(modelRegistry, containerReference, itemType, containerFactory, namer, descriptor, itemDescriptorGenerator);

        modelRegistry.createOrReplace(containerInfo.creatorBuilder
                .withProjection(new StaticTypeDomainObjectContainerModelProjection<C, I>(containerType, itemType, containerInfo.instantiatorReference, containerInfo.storeReference))
                .withProjection(new UnmanagedModelProjection<P>(publicType, true, true))
                .build());
    }

    public static Transformer<String, String> itemDescriptor(String parentDescriptor) {
        return new StandardItemDescriptorFactory(parentDescriptor);
    }

    private static class StandardItemDescriptorFactory implements Transformer<String, String> {
        private final String descriptor;

        public StandardItemDescriptorFactory(String descriptor) {
            this.descriptor = descriptor;
        }

        @Override
        public String transform(String s) {
            return descriptor + '.' + s + "()";
        }
    }
}

