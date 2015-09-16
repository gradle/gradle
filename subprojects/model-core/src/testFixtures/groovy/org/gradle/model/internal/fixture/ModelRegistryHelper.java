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

import org.gradle.api.Action;
import org.gradle.api.NamedDomainObjectFactory;
import org.gradle.api.Nullable;
import org.gradle.api.Transformer;
import org.gradle.api.internal.DefaultPolymorphicNamedEntityInstantiator;
import org.gradle.api.internal.PolymorphicNamedEntityInstantiator;
import org.gradle.api.internal.rules.DefaultRuleAwarePolymorphicNamedEntityInstantiator;
import org.gradle.api.internal.rules.RuleAwarePolymorphicNamedEntityInstantiator;
import org.gradle.internal.*;
import org.gradle.model.ModelMap;
import org.gradle.model.RuleSource;
import org.gradle.model.collection.internal.PolymorphicModelMapProjection;
import org.gradle.model.internal.core.*;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;
import org.gradle.model.internal.core.rule.describe.SimpleModelRuleDescriptor;
import org.gradle.model.internal.inspect.MethodModelRuleExtractors;
import org.gradle.model.internal.inspect.ModelRuleExtractor;
import org.gradle.model.internal.manage.schema.extract.DefaultConstructableTypesRegistry;
import org.gradle.model.internal.manage.schema.extract.DefaultModelSchemaStore;
import org.gradle.model.internal.registry.DefaultModelRegistry;
import org.gradle.model.internal.registry.ModelRegistry;
import org.gradle.model.internal.registry.ModelRegistryScope;
import org.gradle.model.internal.registry.UnboundModelRulesException;
import org.gradle.model.internal.type.ModelType;
import org.gradle.model.internal.type.ModelTypes;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.gradle.model.internal.core.ModelActionRole.Mutate;
import static org.gradle.model.internal.core.ModelPath.nonNullValidatedPath;

/**
 * A helper for adding rules to a model registry.
 *
 * Allows unsafe use of the model registry by allow registering of rules that can close over external, unmanaged, state.
 */
public class ModelRegistryHelper implements ModelRegistry {

    private final ModelRegistry modelRegistry;

    public ModelRegistryHelper() {
        this(new DefaultModelRegistry(new ModelRuleExtractor(MethodModelRuleExtractors.coreExtractors(DefaultModelSchemaStore.getInstance(), new DefaultNodeInitializerRegistry(DefaultModelSchemaStore.getInstance(), new DefaultConstructableTypesRegistry())))));
    }

    public ModelRegistryHelper(ModelRegistryScope modelRegistryScope) {
        this(modelRegistryScope.getModelRegistry());
    }

    public ModelRegistryHelper(ModelRegistry modelRegistry) {
        this.modelRegistry = modelRegistry;
    }

    public ModelRuleDescriptor desc(String p) {
        return new SimpleModelRuleDescriptor(p);
    }

    public ModelPath path(String p) {
        return ModelPath.path(p);
    }

    @Override
    public <T> T realize(ModelPath path, ModelType<T> type) {
        return modelRegistry.realize(path, type);
    }

    @Override
    @Nullable
    public MutableModelNode atState(ModelPath path, ModelNode.State state) {
        return (MutableModelNode) modelRegistry.atState(path, state);
    }

    public MutableModelNode atState(String path, ModelNode.State state) {
        return atState(ModelPath.path(path), state);
    }

    @Override
    @Nullable
    public MutableModelNode atStateOrLater(ModelPath path, ModelNode.State state) {
        return (MutableModelNode) modelRegistry.atStateOrLater(path, state);
    }

    @Override
    public ModelNode.State state(ModelPath path) {
        return modelRegistry.state(path);
    }

    public ModelNode.State state(String path) {
        return modelRegistry.state(ModelPath.path(path));
    }

    @Override
    @Nullable
    public <T> T find(ModelPath path, ModelType<T> type) {
        return modelRegistry.find(path, type);
    }

    @Override
    public ModelNode realizeNode(ModelPath path) {
        return modelRegistry.realizeNode(path);
    }

    @Override
    public void remove(ModelPath path) {
        modelRegistry.remove(path);
    }

    @Override
    public ModelRegistryHelper replace(ModelCreator newCreator) {
        modelRegistry.replace(newCreator);
        return this;
    }

    @Override
    public void bindAllReferences() throws UnboundModelRulesException {
        modelRegistry.bindAllReferences();
    }

    public ModelRegistry getModelRegistry() {
        return modelRegistry;
    }

    public ModelActionBuilder<Object> action() {
        return ModelActionBuilder.of();
    }

    public <C> ModelRegistryHelper createInstance(String path, final C c) {
        return create(instanceCreator(path, c));
    }

    public <C> ModelRegistryHelper create(String path, final C c, Action<? super C> action) {
        return create(creator(path, c, action));
    }

    private <C> ModelCreator creator(String path, C c, Action<? super C> action) {
        return creator(path).unmanaged(c, action);
    }

    public ModelRegistryHelper create(ModelCreator creator) {
        modelRegistry.create(creator);
        return this;
    }


    @Override
    public ModelRegistryHelper createOrReplace(ModelCreator newCreator) {
        modelRegistry.createOrReplace(newCreator);
        return this;
    }

    @Override
    public <T> ModelRegistryHelper configure(ModelActionRole role, ModelAction<T> action) {
        modelRegistry.configure(role, action);
        return this;
    }

    @Override
    public <T> ModelRegistry configure(ModelActionRole role, ModelAction<T> action, ModelPath scope) {
        modelRegistry.configure(role, action, scope);
        return this;
    }

    @Override
    public ModelRegistry apply(Class<? extends RuleSource> rules) {
        return modelRegistry.apply(rules);
    }

    @Override
    public MutableModelNode getRoot() {
        return modelRegistry.getRoot();
    }

    @Override
    public MutableModelNode node(ModelPath path) {
        return modelRegistry.node(path);
    }

    @Nullable
    public MutableModelNode node(String path) {
        return node(ModelPath.path(path));
    }

    @Override
    public void prepareForReuse() {
        modelRegistry.prepareForReuse();
    }

    public ModelRegistryHelper create(String path, Transformer<? extends ModelCreator, ? super ModelCreatorBuilder> def) {
        return create(ModelPath.path(path), def);
    }

    public ModelRegistryHelper create(ModelPath path, Transformer<? extends ModelCreator, ? super ModelCreatorBuilder> def) {
        modelRegistry.create(def.transform(creator(path)));
        return this;
    }

    public ModelCreatorBuilder creator(String path) {
        return creator(ModelPath.path(path));
    }

    public <I> ModelRegistryHelper modelMap(String path, final Class<I> itemType, final Action<? super PolymorphicNamedEntityInstantiator<I>> registrations) {
        configure(ModelActionRole.Initialize, ModelReference.of(path, instantiatorType(itemType)), new Action<RuleAwarePolymorphicNamedEntityInstantiator<I>>() {
            @Override
            public void execute(final RuleAwarePolymorphicNamedEntityInstantiator<I> instantiator) {
                registrations.execute(new PolymorphicNamedEntityInstantiator<I>() {
                    @Override
                    public Set<? extends Class<? extends I>> getCreatableTypes() {
                        return instantiator.getCreatableTypes();
                    }

                    @Override
                    public <U extends I> void registerFactory(Class<U> type, NamedDomainObjectFactory<? extends U> factory) {
                        instantiator.registerFactory(type, factory, new SimpleModelRuleDescriptor("ModelRegistryHelper.modelMap"));
                    }

                    @Override
                    public <S extends I> S create(String name, Class<S> type) {
                        return instantiator.create(name, type);
                    }
                });
            }
        });
        return create(path, new Transformer<ModelCreator, ModelCreatorBuilder>() {
            @Override
            public ModelCreator transform(ModelCreatorBuilder modelCreatorBuilder) {
                return modelCreatorBuilder.modelMap(itemType);
            }
        });

    }

    public <I> ModelRegistryHelper mutateModelMap(final String path, final Class<I> itemType, final Action<? super ModelMap<I>> action) {
        return mutate(new Transformer<ModelAction<?>, ModelActionBuilder<Object>>() {
            @Override
            public ModelAction<?> transform(ModelActionBuilder<Object> builder) {
                return builder.path(path).type(ModelTypes.modelMap(itemType)).action(action);
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

    public ModelRegistryHelper configure(ModelActionRole role, Transformer<? extends ModelAction<?>, ? super ModelActionBuilder<Object>> def) {
        return configure(role, def.transform(ModelActionBuilder.of()));
    }

    public ModelRegistryHelper mutate(Transformer<? extends ModelAction<?>, ? super ModelActionBuilder<Object>> def) {
        return configure(Mutate, def);
    }

    public <T> ModelRegistryHelper mutate(Class<T> type, Action<? super T> action) {
        return apply(Mutate, type, action);
    }

    public <T> ModelRegistryHelper mutate(ModelType<T> type, Action<? super T> action) {
        return apply(Mutate, type, action);
    }

    public <T> ModelRegistryHelper mutate(ModelReference<T> reference, Action<? super T> action) {
        return configure(Mutate, reference, action);
    }

    public ModelRegistryHelper mutate(final String path, final Action<? super MutableModelNode> action) {
        return configure(Mutate, new Transformer<ModelAction<?>, ModelActionBuilder<Object>>() {
            @Override
            public ModelAction<?> transform(ModelActionBuilder<Object> objectModelActionBuilder) {
                return objectModelActionBuilder.path(path).node(action);
            }
        });
    }

    public ModelRegistryHelper apply(String path, final Class<? extends RuleSource> rules) {
        return mutate(path, new Action<MutableModelNode>() {
            @Override
            public void execute(MutableModelNode mutableModelNode) {
                mutableModelNode.applyToSelf(rules);
            }
        });
    }

    private <T> ModelRegistryHelper apply(ModelActionRole role, final Class<T> type, final Action<? super T> action) {
        return apply(role, ModelType.of(type), action);
    }

    private <T> ModelRegistryHelper apply(ModelActionRole role, final ModelType<T> type, final Action<? super T> action) {
        return configure(role, ModelReference.of(type), action);
    }

    private <T> ModelRegistryHelper configure(ModelActionRole role, final ModelReference<T> reference, final Action<? super T> action) {
        return configure(role, new Transformer<ModelAction<?>, ModelActionBuilder<Object>>() {
            @Override
            public ModelAction<?> transform(ModelActionBuilder<Object> objectModelActionBuilder) {
                return objectModelActionBuilder.path(reference.getPath()).type(reference.getType()).action(action);
            }
        });
    }

    public <T> T get(String path, Class<T> type) {
        return modelRegistry.realize(nonNullValidatedPath(path), ModelType.of(type));
    }

    public Object get(String path) {
        return get(path, Object.class);
    }

    public Object get(ModelPath path) {
        return get(path.toString());
    }

    public Object realize(String path) {
        return modelRegistry.realize(nonNullValidatedPath(path), ModelType.UNTYPED);
    }

    public <T> T realize(String path, Class<T> type) {
        return modelRegistry.realize(nonNullValidatedPath(path), ModelType.of(type));
    }

    public static <C> ModelCreator creator(String path, Class<C> type, String inputPath, final Transformer<? extends C, Object> action) {
        return creator(path, ModelType.of(type), inputPath, action);
    }

    public static <C> ModelCreator creator(String path, final ModelType<C> modelType, String inputPath, final Transformer<? extends C, Object> action) {
        return ModelCreators.of(ModelPath.path(path), new BiAction<MutableModelNode, List<ModelView<?>>>() {
            @Override
            public void execute(MutableModelNode mutableModelNode, List<ModelView<?>> inputs) {
                mutableModelNode.setPrivateData(modelType, action.transform(inputs.get(0).getInstance()));
            }
        }).withProjection(new UnmanagedModelProjection<C>(modelType, true, true))
            .inputs(refs(ModelReference.of(inputPath)))
            .descriptor("create " + path)
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
            return build(NO_REFS, new TriAction<MutableModelNode, T, List<ModelView<?>>>() {
                @Override
                public void execute(MutableModelNode mutableModelNode, T t, List<ModelView<?>> inputs) {
                    action.execute(t);
                }
            });
        }

        public ModelAction<T> node(final Action<? super MutableModelNode> action) {
            return build(NO_REFS, new TriAction<MutableModelNode, T, List<ModelView<?>>>() {
                @Override
                public void execute(MutableModelNode mutableModelNode, T t, List<ModelView<?>> inputs) {
                    action.execute(mutableModelNode);
                }
            });
        }

        public <I> ModelAction<T> action(ModelPath modelPath, ModelType<I> inputType, BiAction<? super T, ? super I> action) {
            return action(modelPath, inputType, inputType.toString(), action);
        }

        public <I> ModelAction<T> action(String modelPath, ModelType<I> inputType, BiAction<? super T, ? super I> action) {
            return action(modelPath, inputType, modelPath, action);
        }

        public <I> ModelAction<T> action(final ModelPath modelPath, final ModelType<I> inputType, String referenceDescription, final BiAction<? super T, ? super I> action) {
            return action(ModelReference.of(modelPath, inputType, referenceDescription), action);
        }

        public <I> ModelAction<T> action(final ModelReference<I> inputReference, final BiAction<? super T, ? super I> action) {
            return build(refs(inputReference), new TriAction<MutableModelNode, T, List<ModelView<?>>>() {
                @Override
                public void execute(MutableModelNode mutableModelNode, T t, List<ModelView<?>> inputs) {
                    action.execute(t, ModelViews.assertType(inputs.get(0), inputReference.getType()).getInstance());
                }
            });
        }

        public <I> ModelAction<T> action(final String modelPath, final ModelType<I> inputType, String referenceDescription, final BiAction<? super T, ? super I> action) {
            return action(ModelPath.path(modelPath), inputType, referenceDescription, action);
        }

        public <I> ModelAction<T> action(final ModelType<I> inputType, final BiAction<? super T, ? super I> action) {
            return action((ModelPath) null, inputType, action);
        }

        public <I> ModelAction<T> action(final Class<I> inputType, final BiAction<? super T, ? super I> action) {
            return action(ModelType.of(inputType), action);
        }

        private ModelAction<T> build(List<ModelReference<?>> references, TriAction<? super MutableModelNode, ? super T, ? super List<ModelView<?>>> action) {
            return toAction(references, action, path, type, descriptor);
        }

        private static <T> ModelAction<T> toAction(final List<ModelReference<?>> references, final TriAction<? super MutableModelNode, ? super T, ? super List<ModelView<?>>> action, final ModelPath path, final ModelType<T> type, final ModelRuleDescriptor descriptor) {
            return DirectNodeInputUsingModelAction.of(ModelReference.of(path, type), descriptor, references, new TriAction<MutableModelNode, T, List<ModelView<?>>>() {
                @Override
                public void execute(MutableModelNode modelNode, T t, List<ModelView<?>> inputs) {
                    action.execute(modelNode, t, inputs);
                }
            });
        }
    }

    public static class ModelCreatorBuilder {
        private final ModelPath path;
        private boolean ephemeral;
        private ModelRuleDescriptor descriptor = new SimpleModelRuleDescriptor("tester");

        public ModelCreatorBuilder(ModelPath path) {
            this.path = path;
            descriptor = new SimpleModelRuleDescriptor(path + " creator");
        }

        public ModelCreatorBuilder descriptor(String descriptor) {
            return descriptor(new SimpleModelRuleDescriptor(descriptor));
        }

        public ModelCreatorBuilder ephemeral(boolean flag) {
            this.ephemeral = flag;
            return this;
        }

        public ModelCreatorBuilder descriptor(ModelRuleDescriptor descriptor) {
            this.descriptor = descriptor;
            return this;
        }

        public <C> ModelCreator unmanaged(final Class<C> modelType, String inputPath, final Transformer<? extends C, Object> action) {
            return unmanaged(modelType, inputPath, inputPath, action);
        }

        public <C> ModelCreator unmanaged(final Class<C> modelType, String inputPath, String referenceDescription, final Transformer<? extends C, Object> action) {
            return unmanaged(ModelType.of(modelType), inputPath, referenceDescription, action);
        }

        public <C> ModelCreator unmanaged(final ModelType<C> modelType, String inputPath, final Transformer<? extends C, Object> action) {
            return unmanaged(modelType, inputPath, inputPath, action);
        }

        public <C> ModelCreator unmanaged(final ModelType<C> modelType, String inputPath, String inputDescriptor, final Transformer<? extends C, Object> action) {
            return ModelCreators.of(path, new BiAction<MutableModelNode, List<ModelView<?>>>() {
                @Override
                public void execute(MutableModelNode mutableModelNode, List<ModelView<?>> inputs) {
                    mutableModelNode.setPrivateData(modelType, action.transform(inputs.get(0).getInstance()));
                }
            }).withProjection(new UnmanagedModelProjection<C>(modelType, true, true))
                .inputs(ModelReference.of(inputPath, ModelType.UNTYPED, inputDescriptor))
                .descriptor(descriptor)
                .ephemeral(ephemeral)
                .build();
        }

        public <C, I> ModelCreator unmanaged(Class<C> type, final Class<I> inputType, final Transformer<? extends C, ? super I> action) {
            return unmanaged(ModelType.of(type), ModelType.of(inputType), action);
        }

        public <C, I> ModelCreator unmanaged(final ModelType<C> modelType, final ModelType<I> inputModelType, final Transformer<? extends C, ? super I> action) {
            return ModelCreators.of(path, new BiAction<MutableModelNode, List<ModelView<?>>>() {
                @Override
                public void execute(MutableModelNode mutableModelNode, List<ModelView<?>> inputs) {
                    mutableModelNode.setPrivateData(modelType, action.transform(ModelViews.assertType(inputs.get(0), inputModelType).getInstance()));
                }
            }).withProjection(new UnmanagedModelProjection<C>(modelType, true, true))
                .inputs(ModelReference.of(inputModelType))
                .ephemeral(ephemeral)
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
            return ModelCreators.of(path, new BiAction<MutableModelNode, List<ModelView<?>>>() {
                @Override
                public void execute(MutableModelNode mutableModelNode, List<ModelView<?>> inputs) {
                    mutableModelNode.setPrivateData(modelType, initializer.create());
                }
            }).withProjection(new UnmanagedModelProjection<C>(modelType, true, true))
                .descriptor(descriptor)
                .ephemeral(ephemeral)
                .build();
        }

        public <C> ModelCreator unmanagedNode(Class<C> modelType, final Action<? super MutableModelNode> action) {
            return unmanagedNode(ModelType.of(modelType), action);
        }

        public <C> ModelCreator unmanagedNode(ModelType<C> modelType, final Action<? super MutableModelNode> action) {
            return ModelCreators.of(path, new BiAction<MutableModelNode, List<ModelView<?>>>() {
                @Override
                public void execute(MutableModelNode mutableModelNode, List<ModelView<?>> inputs) {
                    action.execute(mutableModelNode);
                }
            }).withProjection(new UnmanagedModelProjection<C>(modelType, true, true))
                .descriptor(descriptor)
                .ephemeral(ephemeral)
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

        public <I> ModelCreator modelMap(final Class<I> itemType) {
            final ModelType<RuleAwarePolymorphicNamedEntityInstantiator<I>> instantiatorType = instantiatorType(itemType);

            ModelType<I> modelType = ModelType.of(itemType);
            return ModelCreators.of(
                ModelReference.of(path, instantiatorType),
                new Factory<RuleAwarePolymorphicNamedEntityInstantiator<I>>() {
                    @Override
                    public RuleAwarePolymorphicNamedEntityInstantiator<I> create() {
                        return new DefaultRuleAwarePolymorphicNamedEntityInstantiator<I>(
                            new DefaultPolymorphicNamedEntityInstantiator<I>(itemType, "this collection")
                        );
                    }
                }
            )
                .withProjection(PolymorphicModelMapProjection.of(modelType, NodeBackedModelMap.createUsingParentNode(modelType)))
                .withProjection(UnmanagedModelProjection.of(instantiatorType))
                .descriptor(descriptor)
                .ephemeral(ephemeral)
                .build();
        }
    }

    public static <T> ModelType<RuleAwarePolymorphicNamedEntityInstantiator<T>> instantiatorType(Class<T> typeClass) {
        return new ModelType.Builder<RuleAwarePolymorphicNamedEntityInstantiator<T>>() {
        }.where(new ModelType.Parameter<T>() {
        }, ModelType.of(typeClass)).build();
    }
}
