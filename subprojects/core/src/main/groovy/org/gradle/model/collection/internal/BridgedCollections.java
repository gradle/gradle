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
import org.gradle.model.internal.type.ModelType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public abstract class BridgedCollections {

    private static final Logger LOGGER = LoggerFactory.getLogger(BridgedCollections.class);

    private BridgedCollections() {
    }

    private static <I, C extends PolymorphicDomainObjectContainerInternal<I>, P> ModelCreators.Builder creator(final ModelType<C> containerType, ModelType<P> publicType, ModelType<I> itemType, final ModelPath modelPath, final Transformer<? extends C, ? super MutableModelNode> containerFactory, final Namer<? super I> namer, String descriptor, final Transformer<String, String> itemDescriptorGenerator) {
        return ModelCreators.of(
                ModelReference.of(modelPath, containerType),
                new BiAction<MutableModelNode, List<ModelView<?>>>() {
                    public void execute(final MutableModelNode modelNode, List<ModelView<?>> inputs) {
                        C container = containerFactory.transform(modelNode);
                        modelNode.setPrivateData(containerType, container);
                        container.all(new Action<I>() {
                            public void execute(final I item) {
                                String name = namer.determineName(item);

                                // For now, ignore elements added after the container has been closed
                                if (!modelNode.isMutable()) {
                                    LOGGER.debug("Ignoring element '{}' added to '{}' after it is closed.", modelPath, name);
                                    return;
                                }

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
                                String name = namer.determineName(item);
                                modelNode.removeLink(name);
                            }
                        });
                    }
                }
        )
                .simpleDescriptor(descriptor);
    }

    public static <I, C extends PolymorphicDomainObjectContainerInternal<I>, P /* super C */> ModelCreator dynamicTypes(
            final ModelType<C> containerType,
            final ModelType<P> publicType,
            final ModelType<I> itemType,
            final ModelPath modelPath,
            final C container,
            final Namer<? super I> namer,
            final String descriptor,
            final Transformer<String, String> itemDescriptorGenerator
    ) {
        return creator(containerType, publicType, itemType, modelPath, Transformers.constant(container), namer, descriptor, itemDescriptorGenerator)
                .withProjection(new DynamicTypesDomainObjectContainerModelProjection<C, I>(container, itemType.getConcreteClass()))
                .withProjection(new UnmanagedModelProjection<P>(publicType, true, true))
                .build();
    }

    public static <I, C extends PolymorphicDomainObjectContainerInternal<I>, P /* super C */> ModelCreator staticTypes(
            final ModelType<C> containerType,
            final ModelType<P> publicType,
            final ModelType<I> itemType,
            final ModelPath modelPath,
            final Transformer<? extends C, ? super MutableModelNode> containerFactory,
            final Namer<? super I> namer,
            final String descriptor,
            final Transformer<String, String> itemDescriptorGenerator
    ) {
        return creator(containerType, publicType, itemType, modelPath, containerFactory, namer, descriptor, itemDescriptorGenerator)
                .withProjection(new StaticTypeDomainObjectContainerModelProjection<C, I>(containerType, itemType.getConcreteClass()))
                .withProjection(new UnmanagedModelProjection<P>(publicType, true, true))
                .build();
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

