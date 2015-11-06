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
import org.gradle.model.collection.internal.ChildNodeInitializerStrategyAccessors;
import org.gradle.model.collection.internal.ModelMapModelProjection;
import org.gradle.model.internal.core.*;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;
import org.gradle.model.internal.core.rule.describe.SimpleModelRuleDescriptor;
import org.gradle.model.internal.inspect.MethodModelRuleExtractors;
import org.gradle.model.internal.inspect.ModelRuleExtractor;
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
        this(new DefaultModelRegistry(new ModelRuleExtractor(MethodModelRuleExtractors.coreExtractors(DefaultModelSchemaStore.getInstance()))));
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
    public ModelRegistryHelper replace(ModelRegistration newRegistration) {
        modelRegistry.replace(newRegistration);
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

    public <C> ModelRegistryHelper registerInstance(String path, final C c) {
        return register(instanceRegistration(path, c));
    }

    public <C> ModelRegistryHelper register(String path, final C c, Action<? super C> action) {
        return register(registration(path, c, action));
    }

    private <C> ModelRegistration registration(String path, C c, Action<? super C> action) {
        return registration(path).unmanaged(c, action);
    }

    public ModelRegistryHelper register(ModelRegistration registration) {
        modelRegistry.register(registration);
        return this;
    }

    @Override
    public ModelRegistryHelper registerOrReplace(ModelRegistration newRegistration) {
        modelRegistry.registerOrReplace(newRegistration);
        return this;
    }

    @Override
    public ModelRegistryHelper configure(ModelActionRole role, ModelAction action) {
        modelRegistry.configure(role, action);
        return this;
    }

    @Override
    public ModelRegistry configure(ModelActionRole role, ModelAction action, ModelPath scope) {
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

    public ModelRegistryHelper register(String path, Transformer<? extends ModelRegistration, ? super ModelRegistrationBuilder> def) {
        return register(ModelPath.path(path), def);
    }

    public ModelRegistryHelper register(ModelPath path, Transformer<? extends ModelRegistration, ? super ModelRegistrationBuilder> def) {
        modelRegistry.register(def.transform(registration(path)));
        return this;
    }

    public ModelRegistrationBuilder registration(String path) {
        return registration(ModelPath.path(path));
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
        return register(path, new Transformer<ModelRegistration, ModelRegistrationBuilder>() {
            @Override
            public ModelRegistration transform(ModelRegistrationBuilder modelRegistrationBuilder) {
                return modelRegistrationBuilder.modelMap(itemType);
            }
        });

    }

    public <I> ModelRegistryHelper mutateModelMap(final String path, final Class<I> itemType, final Action<? super ModelMap<I>> action) {
        return mutate(new Transformer<ModelAction, ModelActionBuilder<Object>>() {
            @Override
            public ModelAction transform(ModelActionBuilder<Object> builder) {
                return builder.path(path).type(ModelTypes.modelMap(itemType)).action(action);
            }
        });
    }

    public ModelRegistration registration(String path, Transformer<? extends ModelRegistration, ? super ModelRegistrationBuilder> action) {
        return action.transform(registration(ModelPath.path(path)));
    }

    public <C> ModelRegistration instanceRegistration(String path, final C c) {
        return registration(path).unmanaged(c);
    }

    public ModelRegistrationBuilder registration(ModelPath path) {
        return new ModelRegistrationBuilder(path);
    }

    public ModelRegistryHelper configure(ModelActionRole role, Transformer<? extends ModelAction, ? super ModelActionBuilder<Object>> def) {
        return configure(role, def.transform(ModelActionBuilder.of()));
    }

    public ModelRegistryHelper mutate(Transformer<? extends ModelAction, ? super ModelActionBuilder<Object>> def) {
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
        return configure(Mutate, new Transformer<ModelAction, ModelActionBuilder<Object>>() {
            @Override
            public ModelAction transform(ModelActionBuilder<Object> objectModelActionBuilder) {
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
        return configure(role, new Transformer<ModelAction, ModelActionBuilder<Object>>() {
            @Override
            public ModelAction transform(ModelActionBuilder<Object> objectModelActionBuilder) {
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

    public static <C> ModelRegistration registration(String path, Class<C> type, String inputPath, final Transformer<? extends C, Object> action) {
        return registration(path, ModelType.of(type), inputPath, action);
    }

    public static <C> ModelRegistration registration(String path, final ModelType<C> modelType, String inputPath, final Transformer<? extends C, Object> action) {
        return ModelRegistrations.of(ModelPath.path(path), ModelReference.of(inputPath), new BiAction<MutableModelNode, List<ModelView<?>>>() {
            @Override
            public void execute(MutableModelNode mutableModelNode, List<ModelView<?>> inputs) {
                mutableModelNode.setPrivateData(modelType, action.transform(inputs.get(0).getInstance()));
            }
        })
            .withProjection(new UnmanagedModelProjection<C>(modelType, true, true))
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

        public ModelAction action(final Action<? super T> action) {
            return build(NO_REFS, new TriAction<MutableModelNode, T, List<ModelView<?>>>() {
                @Override
                public void execute(MutableModelNode mutableModelNode, T t, List<ModelView<?>> inputs) {
                    action.execute(t);
                }
            });
        }

        public ModelAction node(final Action<? super MutableModelNode> action) {
            return toAction(action, path, type, descriptor);
        }

        public <I> ModelAction action(ModelPath modelPath, ModelType<I> inputType, BiAction<? super T, ? super I> action) {
            return action(modelPath, inputType, inputType.toString(), action);
        }

        public <I> ModelAction action(String modelPath, ModelType<I> inputType, BiAction<? super T, ? super I> action) {
            return action(modelPath, inputType, modelPath, action);
        }

        public <I> ModelAction action(final ModelPath modelPath, final ModelType<I> inputType, String referenceDescription, final BiAction<? super T, ? super I> action) {
            return action(ModelReference.of(modelPath, inputType, referenceDescription), action);
        }

        public <I> ModelAction action(final ModelReference<I> inputReference, final BiAction<? super T, ? super I> action) {
            return build(refs(inputReference), new TriAction<MutableModelNode, T, List<ModelView<?>>>() {
                @Override
                public void execute(MutableModelNode mutableModelNode, T t, List<ModelView<?>> inputs) {
                    action.execute(t, ModelViews.assertType(inputs.get(0), inputReference.getType()).getInstance());
                }
            });
        }

        public <I> ModelAction action(final String modelPath, final ModelType<I> inputType, String referenceDescription, final BiAction<? super T, ? super I> action) {
            return action(ModelPath.path(modelPath), inputType, referenceDescription, action);
        }

        public <I> ModelAction action(final ModelType<I> inputType, final BiAction<? super T, ? super I> action) {
            return action((ModelPath) null, inputType, action);
        }

        public <I> ModelAction action(final Class<I> inputType, final BiAction<? super T, ? super I> action) {
            return action(ModelType.of(inputType), action);
        }

        private ModelAction build(List<ModelReference<?>> references, TriAction<? super MutableModelNode, ? super T, ? super List<ModelView<?>>> action) {
            return toAction(references, action, path, type, descriptor);
        }

        private static <T> ModelAction toAction(final List<ModelReference<?>> references, final TriAction<? super MutableModelNode, ? super T, ? super List<ModelView<?>>> action, final ModelPath path, final ModelType<T> type, final ModelRuleDescriptor descriptor) {
            return DirectNodeInputUsingModelAction.of(ModelReference.of(path, type), descriptor, references, new TriAction<MutableModelNode, T, List<ModelView<?>>>() {
                @Override
                public void execute(MutableModelNode modelNode, T t, List<ModelView<?>> inputs) {
                    action.execute(modelNode, t, inputs);
                }
            });
        }

        private static <T> ModelAction toAction(Action<? super MutableModelNode> action, final ModelPath path, final ModelType<T> type, final ModelRuleDescriptor descriptor) {
            return DirectNodeNoInputsModelAction.of(ModelReference.of(path, type), descriptor, action);
        }
    }

    public static class ModelRegistrationBuilder {
        private final ModelPath path;
        private boolean ephemeral;
        private ModelRuleDescriptor descriptor = new SimpleModelRuleDescriptor("tester");

        public ModelRegistrationBuilder(ModelPath path) {
            this.path = path;
            descriptor = new SimpleModelRuleDescriptor(path + " creator");
        }

        public ModelRegistrationBuilder descriptor(String descriptor) {
            return descriptor(new SimpleModelRuleDescriptor(descriptor));
        }

        public ModelRegistrationBuilder ephemeral(boolean flag) {
            this.ephemeral = flag;
            return this;
        }

        public ModelRegistrationBuilder descriptor(ModelRuleDescriptor descriptor) {
            this.descriptor = descriptor;
            return this;
        }

        public <C> ModelRegistration unmanaged(final Class<C> modelType, String inputPath, final Transformer<? extends C, Object> action) {
            return unmanaged(modelType, inputPath, inputPath, action);
        }

        public <C> ModelRegistration unmanaged(final Class<C> modelType, String inputPath, String referenceDescription, final Transformer<? extends C, Object> action) {
            return unmanaged(ModelType.of(modelType), inputPath, referenceDescription, action);
        }

        public <C> ModelRegistration unmanaged(final ModelType<C> modelType, String inputPath, final Transformer<? extends C, Object> action) {
            return unmanaged(modelType, inputPath, inputPath, action);
        }

        public <C> ModelRegistration unmanaged(final ModelType<C> modelType, String inputPath, String inputDescriptor, final Transformer<? extends C, Object> action) {
            return ModelRegistrations.of(path, ModelReference.of(inputPath, ModelType.UNTYPED, inputDescriptor), new BiAction<MutableModelNode, List<ModelView<?>>>() {
                @Override
                public void execute(MutableModelNode mutableModelNode, List<ModelView<?>> inputs) {
                    mutableModelNode.setPrivateData(modelType, action.transform(inputs.get(0).getInstance()));
                }
            })
                .withProjection(new UnmanagedModelProjection<C>(modelType, true, true))
                .descriptor(descriptor)
                .ephemeral(ephemeral)
                .build();
        }

        public <C, I> ModelRegistration unmanaged(Class<C> type, final Class<I> inputType, final Transformer<? extends C, ? super I> action) {
            return unmanaged(ModelType.of(type), ModelType.of(inputType), action);
        }

        public <C, I> ModelRegistration unmanaged(final ModelType<C> modelType, final ModelType<I> inputModelType, final Transformer<? extends C, ? super I> action) {
            return ModelRegistrations.of(path, ModelReference.of(inputModelType), new BiAction<MutableModelNode, List<ModelView<?>>>() {
                @Override
                public void execute(MutableModelNode mutableModelNode, List<ModelView<?>> inputs) {
                    mutableModelNode.setPrivateData(modelType, action.transform(ModelViews.assertType(inputs.get(0), inputModelType).getInstance()));
                }
            }).withProjection(new UnmanagedModelProjection<C>(modelType, true, true))
                .ephemeral(ephemeral)
                .descriptor(descriptor)
                .build();
        }

        public <C> ModelRegistration unmanaged(Class<C> type, final Factory<? extends C> initializer) {
            return unmanaged(ModelType.of(type), initializer);
        }

        public <C> ModelRegistration unmanaged(Class<C> type, final C c) {
            return unmanaged(ModelType.of(type), Factories.constant(c));
        }

        private <C> ModelRegistration unmanaged(final ModelType<C> modelType, final Factory<? extends C> initializer) {
            return ModelRegistrations.of(path, new Action<MutableModelNode>() {
                @Override
                public void execute(MutableModelNode mutableModelNode) {
                    mutableModelNode.setPrivateData(modelType, initializer.create());
                }
            })
                .withProjection(new UnmanagedModelProjection<C>(modelType, true, true))
                .descriptor(descriptor)
                .ephemeral(ephemeral)
                .build();
        }

        public <C> ModelRegistration unmanagedNode(Class<C> modelType, final Action<? super MutableModelNode> action) {
            return unmanagedNode(ModelType.of(modelType), action);
        }

        public <C> ModelRegistration unmanagedNode(ModelType<C> modelType, final Action<? super MutableModelNode> action) {
            return ModelRegistrations.of(path, new Action<MutableModelNode>() {
                @Override
                public void execute(MutableModelNode mutableModelNode) {
                    action.execute(mutableModelNode);
                }
            })
                .withProjection(new UnmanagedModelProjection<C>(modelType, true, true))
                .descriptor(descriptor)
                .ephemeral(ephemeral)
                .build();
        }

        public <C> ModelRegistration unmanaged(C c) {
            return unmanaged(c, Actions.doNothing());
        }

        public <C> ModelRegistration unmanaged(final C c, final Action<? super C> action) {
            return unmanaged(ModelType.typeOf(c).getConcreteClass(), new Factory<C>() {
                @Override
                public C create() {
                    action.execute(c);
                    return c;
                }
            });
        }

        public <I> ModelRegistration modelMap(final Class<I> itemType) {
            final ModelType<RuleAwarePolymorphicNamedEntityInstantiator<I>> instantiatorType = instantiatorType(itemType);

            ModelType<I> modelType = ModelType.of(itemType);
            return ModelRegistrations.of(
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
                .withProjection(ModelMapModelProjection.unmanaged(
                    modelType,
                    ChildNodeInitializerStrategyAccessors.of(NodeBackedModelMap.createUsingParentNode(modelType)))
                )
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
