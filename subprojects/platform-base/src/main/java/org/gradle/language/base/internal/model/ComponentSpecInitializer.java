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

package org.gradle.language.base.internal.model;

import org.gradle.api.Action;
import org.gradle.api.Named;
import org.gradle.api.NamedDomainObjectCollection;
import org.gradle.api.Transformer;
import org.gradle.internal.BiAction;
import org.gradle.internal.BiActions;
import org.gradle.internal.Factory;
import org.gradle.language.base.LanguageSourceSet;
import org.gradle.model.internal.core.*;
import org.gradle.model.internal.core.rule.describe.StandardDescriptorFactory;
import org.gradle.model.internal.type.ModelType;
import org.gradle.platform.base.BinarySpec;
import org.gradle.platform.base.BinaryTasksCollection;
import org.gradle.platform.base.ComponentSpec;
import org.gradle.platform.base.internal.ComponentSpecInternal;

public class ComponentSpecInitializer {

    private final static BiAction<MutableModelNode, ComponentSpec> COMPONENT_ACTION = createAction();
    private final static BiAction<MutableModelNode, BinarySpec> BINARY_ACTION = createBinaryAction();

    public static BiAction<MutableModelNode, ComponentSpec> action() {
        return COMPONENT_ACTION;
    }

    public static BiAction<MutableModelNode, BinarySpec> binaryAction() {
        return BINARY_ACTION;
    }

    private static BiAction<MutableModelNode, BinarySpec> createBinaryAction() {
        return new BiAction<MutableModelNode, BinarySpec>() {
            @Override
            public void execute(MutableModelNode node, BinarySpec spec) {
                final ModelType<BinaryTasksCollection> itemType = ModelType.of(BinaryTasksCollection.class);
                ModelCreator itemCreator = ModelCreators.of(node.getPath().child("tasks"), new Action<MutableModelNode>() {
                    @Override
                    public void execute(MutableModelNode modelNode) {
                        BinaryTasksCollection tasks = modelNode.getParent().getPrivateData(ModelType.of(BinarySpec.class)).getTasks();
                        modelNode.setPrivateData(itemType, tasks);
                    }
                })
                    .withProjection(new UnmanagedModelProjection<BinaryTasksCollection>(itemType))
                    .descriptor(new StandardDescriptorFactory(node.getDescriptor()).transform("tasks"))
                    .build();
                node.addLink(itemCreator);
            }
        };
    }

    private static BiAction<MutableModelNode, ComponentSpec> createAction() {
        Transformer<NamedDomainObjectCollection<LanguageSourceSet>, ComponentSpecInternal> sourcesPropertyTransformer = new Transformer<NamedDomainObjectCollection<LanguageSourceSet>, ComponentSpecInternal>() {
            public NamedDomainObjectCollection<LanguageSourceSet> transform(ComponentSpecInternal componentSpec) {
                return componentSpec.getSources();
            }
        };

        return domainObjectCollectionModelRegistrar("sources", namedDomainObjectCollectionOf(LanguageSourceSet.class),
            sourcesPropertyTransformer);
    }

    private static <T> ModelType<NamedDomainObjectCollection<T>> namedDomainObjectCollectionOf(Class<T> type) {
        return new ModelType.Builder<NamedDomainObjectCollection<T>>() {
        }.where(new ModelType.Parameter<T>() {
        }, ModelType.of(type)).build();
    }

    private static <T extends Named, C extends NamedDomainObjectCollection<T>> BiAction<MutableModelNode, ComponentSpec> domainObjectCollectionModelRegistrar(
        final String domainObjectCollectionName, final ModelType<C> collectionType, final Transformer<C, ComponentSpecInternal> collectionTransformer
    ) {
        return domainObjectCollectionModelRegistrar(domainObjectCollectionName, collectionType, collectionTransformer, BiActions.doNothing());
    }

    private static <T extends Named, C extends NamedDomainObjectCollection<T>> BiAction<MutableModelNode, ComponentSpec> domainObjectCollectionModelRegistrar(
        final String domainObjectCollectionName, final ModelType<C> collectionType, final Transformer<C, ComponentSpecInternal> collectionTransformer,
        final BiAction<? super T, ? super ComponentSpecInternal> itemInitializationAction
    ) {
        return new DomainObjectCollectionModelRegistrationAction<T, C>(domainObjectCollectionName, collectionType, collectionTransformer, itemInitializationAction);
    }

    private static <T extends Named, C extends NamedDomainObjectCollection<T>> Action<MutableModelNode> domainObjectCollectionItemModelRegistrar(
        final ModelType<C> collectionType, final StandardDescriptorFactory itemCreatorDescriptorFactory, final BiAction<? super T, ? super ComponentSpecInternal> itemInitializationAction) {
        return new DomainObjectCollectionItemModelRegistrationAction<T, C>(collectionType, itemInitializationAction, itemCreatorDescriptorFactory);
    }

    private static class DomainObjectCollectionItemModelRegistrationAction<T extends Named, C extends NamedDomainObjectCollection<T>> implements Action<MutableModelNode> {
        private final ModelType<C> collectionType;
        private final BiAction<? super T, ? super ComponentSpecInternal> itemInitializationAction;
        private final StandardDescriptorFactory itemCreatorDescriptorFactory;

        public DomainObjectCollectionItemModelRegistrationAction(ModelType<C> collectionType, BiAction<? super T, ? super ComponentSpecInternal> itemInitializationAction, StandardDescriptorFactory itemCreatorDescriptorFactory) {
            this.collectionType = collectionType;
            this.itemInitializationAction = itemInitializationAction;
            this.itemCreatorDescriptorFactory = itemCreatorDescriptorFactory;
        }

        @Override
        public void execute(final MutableModelNode collectionModelNode) {
            collectionModelNode.getPrivateData(collectionType).all(new Action<T>() {
                @Override
                public void execute(T item) {
                    ComponentSpec componentSpec = collectionModelNode.getParent().getPrivateData(ModelType.of(ComponentSpec.class));
                    itemInitializationAction.execute(item, (ComponentSpecInternal) componentSpec);
                    final String name = item.getName();
                    ModelType<T> itemType = ModelType.typeOf(item);
                    ModelReference<T> itemReference = ModelReference.of(collectionModelNode.getPath().child(name), itemType);
                    ModelCreator itemCreator = ModelCreators.unmanagedInstance(itemReference, new Factory<T>() {
                        public T create() {
                            return collectionModelNode.getPrivateData(collectionType).getByName(name);
                        }
                    })
                        .descriptor(itemCreatorDescriptorFactory.transform(name))
                        .build();

                    collectionModelNode.addLink(itemCreator);
                    collectionModelNode.getLink(name).ensureUsable();
                }
            });
        }
    }

    private static class DomainObjectCollectionModelRegistrationAction<T extends Named, C extends NamedDomainObjectCollection<T>> implements BiAction<MutableModelNode, ComponentSpec> {

        private final String domainObjectCollectionName;
        private final ModelType<C> collectionType;
        private final Transformer<C, ComponentSpecInternal> collectionTransformer;
        private final BiAction<? super T, ? super ComponentSpecInternal> itemInitializationAction;

        public DomainObjectCollectionModelRegistrationAction(String domainObjectCollectionName, ModelType<C> collectionType, Transformer<C, ComponentSpecInternal> collectionTransformer,
                                                             BiAction<? super T, ? super ComponentSpecInternal> itemInitializationAction) {
            this.domainObjectCollectionName = domainObjectCollectionName;
            this.collectionType = collectionType;
            this.collectionTransformer = collectionTransformer;
            this.itemInitializationAction = itemInitializationAction;
        }

        @Override
        public void execute(final MutableModelNode mutableModelNode, final ComponentSpec componentSpec) {
            ModelReference<C> reference = ModelReference.of(mutableModelNode.getPath().child(domainObjectCollectionName), collectionType);
            String containerCreatorDescriptor = new StandardDescriptorFactory(mutableModelNode.getDescriptor()).transform(domainObjectCollectionName);

            final StandardDescriptorFactory itemCreatorDescriptorFactory = new StandardDescriptorFactory(containerCreatorDescriptor);

            Factory<C> domainObjectCollectionFactory = new Factory<C>() {
                public C create() {
                    ComponentSpec componentSpec = mutableModelNode.getPrivateData(ModelType.of(ComponentSpec.class));
                    return collectionTransformer.transform((ComponentSpecInternal) componentSpec);
                }
            };
            Action<MutableModelNode> itemRegistrar = domainObjectCollectionItemModelRegistrar(collectionType, itemCreatorDescriptorFactory, itemInitializationAction);
            mutableModelNode.addLink(
                    ModelCreators.unmanagedInstance(reference, domainObjectCollectionFactory, itemRegistrar)
                            .descriptor(containerCreatorDescriptor)
                            .build()
            );
            mutableModelNode.getLink(domainObjectCollectionName).ensureUsable();
        }
    }
}
