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

package org.gradle.model.internal.fixture;

import com.google.common.collect.Lists;
import org.gradle.api.Action;
import org.gradle.api.Nullable;
import org.gradle.api.Transformer;
import org.gradle.internal.*;
import org.gradle.model.collection.CollectionBuilder;
import org.gradle.model.internal.core.*;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;
import org.gradle.model.internal.core.rule.describe.SimpleModelRuleDescriptor;
import org.gradle.model.internal.inspect.DefaultModelRuleSourceApplicator;
import org.gradle.model.internal.inspect.MethodModelRuleExtractors;
import org.gradle.model.internal.inspect.ModelRuleInspector;
import org.gradle.model.internal.inspect.ModelRuleSourceDetector;
import org.gradle.model.internal.manage.schema.extract.DefaultModelSchemaStore;
import org.gradle.model.internal.registry.DefaultModelRegistry;
import org.gradle.model.internal.registry.ModelRegistry;
import org.gradle.model.internal.registry.ModelRegistryScope;
import org.gradle.model.internal.registry.UnboundModelRulesException;
import org.gradle.model.internal.type.ModelType;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.gradle.model.internal.core.ModelActionRole.Mutate;
import static org.gradle.model.internal.core.ModelPath.nonNullValidatedPath;

/**
 * A helper for adding rules to a model registry.
 *
 * Allows unsafe use of the model registry by allow registering of rules that can close over external, unmanaged, state.
 */
public class ModelRegistryHelper implements ModelRegistry {

    private final ModelRegistry modelRegistry;
    private final PluginClassApplicator pluginClassApplicator;
    private final ModelRuleSourceApplicator modelRuleSourceApplicator;

    public ModelRegistryHelper() {
        this(
                new DefaultModelRegistry(null, null),
                new DefaultModelRuleSourceApplicator(new ModelRuleSourceDetector(), new ModelRuleInspector(MethodModelRuleExtractors.coreExtractors(DefaultModelSchemaStore.getInstance()))),
                new NoOpPluginClassApplicator()
        );
    }

    public ModelRegistryHelper(ModelRegistryScope modelRegistryScope) {
        this(modelRegistryScope.getModelRegistry(), null, null);
    }

    public ModelRegistryHelper(ModelRegistry modelRegistry, ModelRuleSourceApplicator modelRuleSourceApplicator, PluginClassApplicator pluginClassApplicator) {
        this.modelRegistry = modelRegistry;
        this.pluginClassApplicator = pluginClassApplicator;
        this.modelRuleSourceApplicator = modelRuleSourceApplicator;
    }

    @Override
    public <T> T realize(ModelPath path, ModelType<T> type) {
        return modelRegistry.realize(path, type);
    }

    @Override
    @Nullable
    public ModelNode atState(ModelPath path, ModelNode.State state) {
        return modelRegistry.atState(path, state);
    }

    @Override
    @Nullable
    public ModelNode atStateOrLater(ModelPath path, ModelNode.State state) {
        return modelRegistry.atStateOrLater(path, state);
    }

    @Override
    public ModelNode.State state(ModelPath path) {
        return modelRegistry.state(path);
    }

    @Override
    @Nullable
    public <T> T find(ModelPath path, ModelType<T> type) {
        return modelRegistry.find(path, type);
    }

    @Override
    public MutableModelNode realizeNode(ModelPath path) {
        return modelRegistry.realizeNode(path);
    }

    public MutableModelNode realizeNode(String path) {
        return realizeNode(ModelPath.path(path));
    }

    @Override
    public void remove(ModelPath path) {
        modelRegistry.remove(path);
    }

    @Override
    public void validate() throws UnboundModelRulesException {
        modelRegistry.validate();
    }

    public ModelRegistry getModelRegistry() {
        return modelRegistry;
    }

    public ModelActionBuilder<Object> action() {
        return ModelActionBuilder.of();
    }

    public <C> ModelRegistryHelper createInstance(String path, final C c) {
        return create(instanceCreator(path, c), ModelPath.ROOT);
    }

    public <C> ModelRegistryHelper create(String path, final C c, Action<? super C> action) {
        return create(creator(path, c, action), ModelPath.ROOT);
    }

    private <C> ModelCreator creator(String path, C c, Action<? super C> action) {
        return creator(path).unmanaged(c, action);
    }

    public ModelRegistryHelper create(ModelCreator creator, ModelPath scope) {
        modelRegistry.create(creator, scope);
        return this;
    }

    public ModelRegistryHelper create(ModelCreator creator) {
        return create(creator, ModelPath.ROOT);
    }

    @Override
    public <T> ModelRegistryHelper apply(ModelActionRole role, ModelAction<T> action, ModelPath scope) {
        modelRegistry.apply(role, action, scope);
        return this;
    }

    public <T> ModelRegistryHelper apply(ModelActionRole role, ModelAction<T> action) {
        return apply(role, action, ModelPath.ROOT);
    }

    public ModelRegistryHelper create(String path, Transformer<? extends ModelCreator, ? super ModelCreatorBuilder> def) {
        return create(ModelPath.path(path), def);
    }

    public ModelRegistryHelper create(ModelPath path, Transformer<? extends ModelCreator, ? super ModelCreatorBuilder> def) {
        modelRegistry.create(def.transform(creator(path)), ModelPath.ROOT);
        return this;
    }

    public ModelCreatorBuilder creator(String path) {
        return creator(ModelPath.path(path));
    }

    public <I> ModelRegistryHelper collection(String path, final Class<I> itemType, final NamedEntityInstantiator<I> instantiator) {
        return create(path, new Transformer<ModelCreator, ModelCreatorBuilder>() {
            @Override
            public ModelCreator transform(ModelCreatorBuilder modelCreatorBuilder) {
                return modelCreatorBuilder.collection(itemType, instantiator, modelRuleSourceApplicator, ModelRegistryHelper.this, pluginClassApplicator);
            }
        });
    }

    public <I> ModelRegistryHelper mutateCollection(final String path, final Class<I> itemType, final Action<? super CollectionBuilder<I>> action) {
        return mutate(new Transformer<ModelAction<?>, ModelActionBuilder<Object>>() {
            @Override
            public ModelAction<?> transform(ModelActionBuilder<Object> builder) {
                return builder.path(path).type(DefaultCollectionBuilder.typeOf(ModelType.of(itemType))).action(action);
            }
        });
    }

    public ModelCreator creator(String path, Transformer<? extends ModelCreator, ? super ModelCreatorBuilder> action) {
        return action.transform(creator(ModelPath.path(path)));
    }

    public <C> ModelCreator instanceCreator(String path, final C c) {
        return creator(path).unmanaged(c);
    }

    public ModelCreatorBuilder creator(ModelPath path) {
        return new ModelCreatorBuilder(path);
    }

    public ModelRegistryHelper apply(ModelActionRole role, Transformer<? extends ModelAction<?>, ? super ModelActionBuilder<Object>> def) {
        return apply(role, def.transform(ModelActionBuilder.of()), ModelPath.ROOT);
    }

    public ModelRegistryHelper mutate(Transformer<? extends ModelAction<?>, ? super ModelActionBuilder<Object>> def) {
        return apply(Mutate, def);
    }

    public <T> ModelRegistryHelper mutate(Class<T> type, Action<? super T> action) {
        return apply(Mutate, type, action);
    }

    private <T> ModelRegistryHelper apply(ModelActionRole role, final Class<T> type, final Action<? super T> action) {
        return apply(role, new Transformer<ModelAction<?>, ModelActionBuilder<Object>>() {
            @Override
            public ModelAction<?> transform(ModelActionBuilder<Object> objectModelActionBuilder) {
                return objectModelActionBuilder.type(type).action(action);
            }
        });
    }

    public <T> T get(String path, Class<T> type) {
        return modelRegistry.realize(nonNullValidatedPath(path), ModelType.of(type));
    }

    public Object get(String path) {
        return get(path, Object.class);
    }

    public void realize(String path) {
        modelRegistry.realize(nonNullValidatedPath(path), ModelType.UNTYPED);
    }

    public static <C> ModelCreator creator(String path, Class<C> type, String inputPath, final Transformer<? extends C, Object> action) {
        return creator(path, ModelType.of(type), inputPath, action);
    }

    public static <C> ModelCreator creator(String path, final ModelType<C> modelType, String inputPath, final Transformer<? extends C, Object> action) {
        return ModelCreators.of(ModelReference.of(path, modelType), new BiAction<MutableModelNode, Inputs>() {
            @Override
            public void execute(MutableModelNode mutableModelNode, Inputs inputs) {
                mutableModelNode.setPrivateData(modelType, action.transform(inputs.get(0, ModelType.untyped()).getInstance()));
            }
        }).withProjection(new UnmanagedModelProjection<C>(modelType, true, true))
                .inputs(refs(ModelReference.of(inputPath)))
                .simpleDescriptor("create " + path)
                .build();
    }


    private static List<ModelReference<?>> refs(ModelReference<?>... refType) {
        return Arrays.asList(refType);
    }

    public static class ModelActionBuilder<T> {

        private static final List<ModelReference<?>> NO_REFS = Collections.emptyList();

        private ModelPath path;
        private ModelType<T> type;
        private ModelRuleDescriptor descriptor;

        private ModelActionBuilder(ModelPath path, ModelType<T> type, ModelRuleDescriptor descriptor) {
            this.path = path;
            this.type = type;
            this.descriptor = descriptor;
        }

        public static ModelActionBuilder<Object> of() {
            return new ModelActionBuilder<Object>(null, ModelType.UNTYPED, new SimpleModelRuleDescriptor("testrule"));
        }

        private <N> ModelActionBuilder<N> copy(ModelType<N> type) {
            return new ModelActionBuilder<N>(path, type, descriptor);
        }

        public ModelActionBuilder<T> path(String path) {
            return this.path(ModelPath.path(path));
        }

        public ModelActionBuilder<T> path(ModelPath path) {
            this.path = path;
            return this;
        }

        public ModelActionBuilder<T> descriptor(String descriptor) {
            return descriptor(new SimpleModelRuleDescriptor(descriptor));
        }

        public ModelActionBuilder<T> descriptor(ModelRuleDescriptor descriptor) {
            this.descriptor = descriptor;
            return this;
        }

        public <N> ModelActionBuilder<N> type(Class<N> type) {
            return type(ModelType.of(type));
        }

        public <N> ModelActionBuilder<N> type(ModelType<N> type) {
            return copy(type);
        }

        public ModelAction<T> action(final Action<? super T> action) {
            return build(NO_REFS, new TriAction<MutableModelNode, T, Inputs>() {
                @Override
                public void execute(MutableModelNode mutableModelNode, T t, Inputs inputs) {
                    action.execute(t);
                }
            });
        }

        public ModelAction<T> node(final Action<? super MutableModelNode> action) {
            return build(NO_REFS, new TriAction<MutableModelNode, T, Inputs>() {
                @Override
                public void execute(MutableModelNode mutableModelNode, T t, Inputs inputs) {
                    action.execute(mutableModelNode);
                }
            });
        }

        public <I> ModelAction<T> action(final String modelPath, final ModelType<I> inputType, final BiAction<? super T, ? super I> action) {
            return build(refs(ModelReference.of(modelPath, inputType)), new TriAction<MutableModelNode, T, Inputs>() {
                @Override
                public void execute(MutableModelNode mutableModelNode, T t, Inputs inputs) {
                    action.execute(t, inputs.get(0, inputType).getInstance());
                }
            });
        }

        public <I> ModelAction<T> action(final ModelType<I> inputType, final BiAction<? super T, ? super I> action) {
            return action(null, inputType, action);
        }

        public <I> ModelAction<T> action(final Class<I> inputType, final BiAction<? super T, ? super I> action) {
            return action(ModelType.of(inputType), action);
        }

        private ModelAction<T> build(List<ModelReference<?>> references, TriAction<? super MutableModelNode, ? super T, ? super Inputs> action) {
            return toAction(references, action, path, type, descriptor);
        }

        private static <T> ModelAction<T> toAction(final List<ModelReference<?>> references, final TriAction<? super MutableModelNode, ? super T, ? super Inputs> action, final ModelPath path, final ModelType<T> type, final ModelRuleDescriptor descriptor) {
            return new ModelAction<T>() {
                @Override
                public ModelReference<T> getSubject() {
                    return ModelReference.of(path, type);
                }

                @Override
                public void execute(MutableModelNode modelNode, T object, Inputs inputs, ModelRuleSourceApplicator modelRuleSourceApplicator, ModelRegistrar modelRegistrar,
                                    PluginClassApplicator pluginClassApplicator) {
                    action.execute(modelNode, object, inputs);
                }

                @Override
                public List<ModelReference<?>> getInputs() {
                    return references;
                }

                @Override
                public ModelRuleDescriptor getDescriptor() {
                    return descriptor;
                }
            };
        }
    }

    public static class ModelCreatorBuilder {
        private final ModelPath path;
        private ModelRuleDescriptor descriptor = new SimpleModelRuleDescriptor("tester");

        public ModelCreatorBuilder(ModelPath path) {
            this.path = path;
        }

        public ModelCreatorBuilder descriptor(String descriptor) {
            return descriptor(new SimpleModelRuleDescriptor(descriptor));
        }

        public ModelCreatorBuilder descriptor(ModelRuleDescriptor descriptor) {
            this.descriptor = descriptor;
            return this;
        }

        public <C> ModelCreator unmanaged(final Class<C> modelType, String inputPath, final Transformer<? extends C, Object> action) {
            return unmanaged(ModelType.of(modelType), inputPath, action);
        }

        public <C> ModelCreator unmanaged(final ModelType<C> modelType, String inputPath, final Transformer<? extends C, Object> action) {
            return ModelCreators.of(ModelReference.of(path), new BiAction<MutableModelNode, Inputs>() {
                @Override
                public void execute(MutableModelNode mutableModelNode, Inputs inputs) {
                    mutableModelNode.setPrivateData(modelType, action.transform(inputs.get(0, ModelType.untyped()).getInstance()));
                }
            }).withProjection(new UnmanagedModelProjection<C>(modelType, true, true))
                    .inputs(ModelReference.of(inputPath))
                    .descriptor(descriptor)
                    .build();
        }

        public <C, I> ModelCreator unmanaged(Class<C> type, final Class<I> inputType, final Transformer<? extends C, ? super I> action) {
            return unmanaged(ModelType.of(type), ModelType.of(inputType), action);
        }

        public <C, I> ModelCreator unmanaged(final ModelType<C> modelType, final ModelType<I> inputModelType, final Transformer<? extends C, ? super I> action) {
            return ModelCreators.of(ModelReference.of(path, modelType), new BiAction<MutableModelNode, Inputs>() {
                @Override
                public void execute(MutableModelNode mutableModelNode, Inputs inputs) {
                    mutableModelNode.setPrivateData(modelType, action.transform(inputs.get(0, inputModelType).getInstance()));
                }
            }).withProjection(new UnmanagedModelProjection<C>(modelType, true, true))
                    .inputs(ModelReference.of(inputModelType))
                    .descriptor(descriptor)
                    .build();
        }

        public <C> ModelCreator unmanaged(Class<C> type, final Factory<? extends C> initializer) {
            return unmanaged(ModelType.of(type), initializer);
        }

        public <C> ModelCreator unmanaged(Class<C> type, final C c) {
            return unmanaged(ModelType.of(type), Factories.constant(c));
        }

        private <C> ModelCreator unmanaged(final ModelType<C> modelType, final Factory<? extends C> initializer) {
            return ModelCreators.of(ModelReference.of(path), new BiAction<MutableModelNode, Inputs>() {
                @Override
                public void execute(MutableModelNode mutableModelNode, Inputs inputs) {
                    mutableModelNode.setPrivateData(modelType, initializer.create());
                }
            }).withProjection(new UnmanagedModelProjection<C>(modelType, true, true))
                    .descriptor(descriptor)
                    .build();
        }

        public <C> ModelCreator unmanagedNode(Class<C> modelType, final Action<? super MutableModelNode> action) {
            return unmanagedNode(ModelType.of(modelType), action);
        }

        public <C> ModelCreator unmanagedNode(ModelType<C> modelType, final Action<? super MutableModelNode> action) {
            return ModelCreators.of(ModelReference.of(path), new BiAction<MutableModelNode, Inputs>() {
                @Override
                public void execute(MutableModelNode mutableModelNode, Inputs inputs) {
                    action.execute(mutableModelNode);
                }
            }).withProjection(new UnmanagedModelProjection<C>(modelType, true, true))
                    .descriptor(descriptor)
                    .build();
        }

        public <C> ModelCreator unmanaged(C c) {
            return unmanaged(c, Actions.doNothing());
        }

        public <C> ModelCreator unmanaged(final C c, final Action<? super C> action) {
            return unmanaged(ModelType.typeOf(c).getConcreteClass(), new Factory<C>() {
                @Override
                public C create() {
                    action.execute(c);
                    return c;
                }
            });
        }

        public <I> ModelCreator collection(Class<I> itemType, final NamedEntityInstantiator<I> instantiator, final ModelRuleSourceApplicator modelRuleSourceApplicator,
                                           final ModelRegistrar modelRegistrar, final PluginClassApplicator pluginClassApplicator) {
            final ModelType<I> itemModelType = ModelType.of(itemType);
            final ModelType<CollectionBuilder<I>> collectionBuilderType = DefaultCollectionBuilder.typeOf(itemModelType);

            return ModelCreators.of(ModelReference.of(path, collectionBuilderType), new BiAction<MutableModelNode, Inputs>() {
                @Override
                public void execute(MutableModelNode node, Inputs inputs) {
                    node.setPrivateData(
                            collectionBuilderType,
                            new DefaultCollectionBuilder<I>(itemModelType, instantiator, Lists.newLinkedList(), descriptor, node, modelRuleSourceApplicator, modelRegistrar, pluginClassApplicator)
                    );
                }
            })
                    .withProjection(new UnmanagedModelProjection<CollectionBuilder<I>>(collectionBuilderType, true, true))
                    .descriptor(descriptor)
                    .build();
        }
    }


    private static class NoOpPluginClassApplicator implements PluginClassApplicator {
        @Override
        public void apply(Class<?> type) {
        }
    }
}
