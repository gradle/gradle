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
package org.gradle.model.internal.fixture;

import org.gradle.api.Action;
import org.gradle.api.NamedDomainObjectFactory;
import org.gradle.api.Transformer;
import org.gradle.api.internal.DefaultPolymorphicNamedEntityInstantiator;
import org.gradle.api.internal.PolymorphicNamedEntityInstantiator;
import org.gradle.api.internal.rules.DefaultRuleAwarePolymorphicNamedEntityInstantiator;
import org.gradle.api.internal.rules.RuleAwarePolymorphicNamedEntityInstantiator;
import org.gradle.internal.Actions;
import org.gradle.internal.BiAction;
import org.gradle.internal.Factories;
import org.gradle.internal.Factory;
import org.gradle.model.ModelMap;
import org.gradle.model.RuleSource;
import org.gradle.model.internal.core.ChildNodeInitializerStrategyAccessors;
import org.gradle.model.internal.core.ModelAction;
import org.gradle.model.internal.core.ModelActionRole;
import org.gradle.model.internal.core.ModelMapModelProjection;
import org.gradle.model.internal.core.ModelNode;
import org.gradle.model.internal.core.ModelPath;
import org.gradle.model.internal.core.ModelReference;
import org.gradle.model.internal.core.ModelRegistration;
import org.gradle.model.internal.core.ModelRegistrations;
import org.gradle.model.internal.core.MutableModelNode;
import org.gradle.model.internal.core.NodeBackedModelMap;
import org.gradle.model.internal.core.NodeInitializerContext;
import org.gradle.model.internal.core.NodeInitializerRegistry;
import org.gradle.model.internal.core.NodePredicate;
import org.gradle.model.internal.core.UnmanagedModelProjection;
import org.gradle.model.internal.core.rule.describe.SimpleModelRuleDescriptor;
import org.gradle.model.internal.registry.DefaultModelRegistry;
import org.gradle.model.internal.registry.ModelRegistry;
import org.gradle.model.internal.type.ModelType;
import org.gradle.model.internal.type.ModelTypes;

import javax.annotation.Nullable;
import java.util.Set;

import static org.gradle.model.internal.core.ModelActionRole.Initialize;
import static org.gradle.model.internal.core.ModelActionRole.Mutate;
import static org.gradle.model.internal.core.NodeInitializerContext.forType;

/**
 * A helper for adding rules to a model registry.
 *
 * Allows unsafe use of the model registry by allow registering of rules that can close over external, unmanaged, state.
 */
public class ModelRegistryHelperExtension {
    // ModelRegistry methods

    public static MutableModelNode atState(DefaultModelRegistry modelRegistry, String path, ModelNode.State state) {
        return (MutableModelNode) modelRegistry.atState(ModelPath.path(path), state);
    }

    public static MutableModelNode atStateOrLater(ModelRegistry modelRegistry, String path, ModelNode.State state) {
        return (MutableModelNode) modelRegistry.atStateOrLater(ModelPath.path(path), state);
    }

    public static ModelNode.State state(ModelRegistry modelRegistry, String path) {
        return modelRegistry.state(ModelPath.path(path));
    }

    @Nullable
    public static MutableModelNode node(DefaultModelRegistry modelRegistry, String path) {
        return modelRegistry.node(ModelPath.path(path));
    }

    public static <C> ModelRegistry registerInstance(ModelRegistry modelRegistry, String path, final C c) {
        return modelRegistry.register(unmanaged(registration(ModelPath.path(path)), c));
    }

    public static <C> ModelRegistry registerInstance(ModelRegistry modelRegistry, String path, final C c, Action<? super C> action) {
        return modelRegistry.register(unmanaged(registration(ModelPath.path(path)), c, action));
    }

    public static <C> ModelRegistry registerWithInitializer(ModelRegistry modelRegistry, String path, Class<C> type, NodeInitializerRegistry nodeInitializerRegistry) {
        NodeInitializerContext<C> nodeInitializerContext = forType(ModelType.of(type));
        ModelRegistration registration = ModelRegistrations.of(ModelPath.path(path), nodeInitializerRegistry.getNodeInitializer(nodeInitializerContext)).descriptor("create " + path).build();
        modelRegistry.register(registration);
        return modelRegistry;
    }

    public static ModelRegistry register(ModelRegistry modelRegistry, String path, Transformer<? extends ModelRegistration, ? super ModelRegistrations.Builder> definition) {
        return register(modelRegistry, ModelPath.path(path), definition);
    }

    public static ModelRegistry register(ModelRegistry modelRegistry, ModelPath path, Transformer<? extends ModelRegistration, ? super ModelRegistrations.Builder> definition) {
        return modelRegistry.register(definition.transform(registration(path)));
    }

    public static <I> ModelRegistry registerModelMap(ModelRegistry modelRegistry, String path, final Class<I> itemType, final Action<? super PolymorphicNamedEntityInstantiator<I>> registrations) {
        configure(modelRegistry, Initialize, ModelReference.of(path, ModelRegistryHelper.instantiatorType(itemType)), new Action<RuleAwarePolymorphicNamedEntityInstantiator<I>>() {
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
        return register(modelRegistry, path, new Transformer<ModelRegistration, ModelRegistrations.Builder>() {
            @Override
            public ModelRegistration transform(ModelRegistrations.Builder modelRegistrationBuilder) {
                return modelMap(modelRegistrationBuilder, itemType);
            }
        });
    }

    public static <I> ModelRegistry mutateModelMap(ModelRegistry modelRegistry, final String path, final Class<I> itemType, final Action<? super ModelMap<I>> action) {
        return mutate(modelRegistry, new Transformer<ModelAction, ModelActionBuilder<Object>>() {
            @Override
            public ModelAction transform(ModelActionBuilder<Object> builder) {
                return builder.path(path).type(ModelTypes.modelMap(itemType)).action(action);
            }
        });
    }

    private static ModelRegistrations.Builder registration(ModelPath path) {
        return ModelRegistrations.of(path).descriptor(path + " creator");
    }

    public static ModelRegistry configure(ModelRegistry modelRegistry, ModelActionRole role, Transformer<? extends ModelAction, ? super ModelActionBuilder<Object>> definition) {
        return modelRegistry.configure(role, definition.transform(ModelActionBuilder.of()));
    }

    public static ModelRegistry mutate(ModelRegistry modelRegistry, Transformer<? extends ModelAction, ? super ModelActionBuilder<Object>> definition) {
        return configure(modelRegistry, Mutate, definition);
    }

    public static <T> ModelRegistry mutate(ModelRegistry modelRegistry, Class<T> type, Action<? super T> action) {
        return applyInternal(modelRegistry, Mutate, type, action);
    }

    public static <T> ModelRegistry mutate(ModelRegistry modelRegistry, ModelType<T> type, Action<? super T> action) {
        return applyInternal(modelRegistry, Mutate, type, action);
    }

    public static <T> ModelRegistry mutate(ModelRegistry modelRegistry, ModelReference<T> reference, Action<? super T> action) {
        return configure(modelRegistry, Mutate, reference, action);
    }

    private static <T> ModelRegistry applyInternal(ModelRegistry modelRegistry, ModelActionRole role, final Class<T> type, final Action<? super T> action) {
        return applyInternal(modelRegistry, role, ModelType.of(type), action);
    }

    private static <T> ModelRegistry applyInternal(ModelRegistry modelRegistry, ModelActionRole role, final ModelType<T> type, final Action<? super T> action) {
        return configure(modelRegistry, role, ModelReference.of(type), action);
    }

    public static ModelRegistry mutate(ModelRegistry modelRegistry, final String path, final Action<? super MutableModelNode> action) {
        return configure(modelRegistry, Mutate, new Transformer<ModelAction, ModelActionBuilder<Object>>() {
            @Override
            public ModelAction transform(ModelActionBuilder<Object> objectModelActionBuilder) {
                return objectModelActionBuilder.path(path).node(action);
            }
        });
    }

    public static ModelRegistry apply(ModelRegistry modelRegistry, String path, final Class<? extends RuleSource> rules) {
        return mutate(modelRegistry, path, new Action<MutableModelNode>() {
            @Override
            public void execute(MutableModelNode mutableModelNode) {
                mutableModelNode.applyToSelf(rules);
            }
        });
    }

    public static <T> ModelRegistry configure(ModelRegistry modelRegistry, ModelActionRole role, final ModelReference<T> reference, final Action<? super T> action) {
        return configure(modelRegistry, role, new Transformer<ModelAction, ModelActionBuilder<Object>>() {
            @Override
            public ModelAction transform(ModelActionBuilder<Object> objectModelActionBuilder) {
                return objectModelActionBuilder.path(reference.getPath()).type(reference.getType()).action(action);
            }
        });
    }

    public static <T> T get(ModelRegistry modelRegistry, String path, Class<T> type) {
        return modelRegistry.realize(ModelPath.nonNullValidatedPath(path), ModelType.of(type));
    }

    public static Object get(ModelRegistry modelRegistry, String path) {
        return get(modelRegistry, path, Object.class);
    }

    public static Object get(ModelRegistry modelRegistry, ModelPath path) {
        return get(modelRegistry, path.toString());
    }

    public static Object realize(ModelRegistry modelRegistry, String path) {
        return modelRegistry.realize(ModelPath.nonNullValidatedPath(path), ModelType.UNTYPED);
    }

    public static <T> T realize(ModelRegistry modelRegistry, String path, Class<T> type) {
        return modelRegistry.realize(ModelPath.nonNullValidatedPath(path), ModelType.of(type));
    }

    // ModelRegistrations.Builder methods

    public static <C> ModelRegistration unmanaged(ModelRegistrations.Builder builder, Class<C> modelType, String inputPath, Transformer<? extends C, Object> action) {
        return unmanaged(builder, modelType, inputPath, inputPath, action);
    }

    public static <C> ModelRegistration unmanaged(ModelRegistrations.Builder builder, Class<C> modelType, String inputPath, String referenceDescription, Transformer<? extends C, Object> action) {
        return unmanaged(builder, ModelType.of(modelType), inputPath, referenceDescription, action);
    }

    public static <C> ModelRegistration unmanaged(ModelRegistrations.Builder builder, ModelType<C> modelType, String inputPath, Transformer<? extends C, Object> action) {
        return unmanaged(builder, modelType, inputPath, inputPath, action);
    }

    public static <C> ModelRegistration unmanaged(ModelRegistrations.Builder builder, final ModelType<C> modelType, String inputPath, String inputDescriptor, final Transformer<? extends C, Object> action) {
        return builder.action(
            ModelActionRole.Create,
            ModelReference.of(inputPath, ModelType.UNTYPED, inputDescriptor),
            new BiAction<MutableModelNode, Object>() {
                @Override
                public void execute(MutableModelNode mutableModelNode, Object input) {
                    mutableModelNode.setPrivateData(modelType, action.transform(input));
                }
            }
        )
            .withProjection(new UnmanagedModelProjection<C>(modelType))
            .build();
    }

    public static <C, I> ModelRegistration unmanaged(ModelRegistrations.Builder builder, Class<C> type, Class<I> inputType, Transformer<? extends C, ? super I> action) {
        return unmanaged(builder, ModelType.of(type), ModelType.of(inputType), action);
    }

    public static <C, I> ModelRegistration unmanaged(ModelRegistrations.Builder builder, final ModelType<C> modelType, ModelType<I> inputModelType, final Transformer<? extends C, ? super I> action) {
        return builder.action(
            ModelActionRole.Create,
            ModelReference.of(inputModelType),
            new BiAction<MutableModelNode, I>() {
                @Override
                public void execute(MutableModelNode mutableModelNode, I input) {
                    mutableModelNode.setPrivateData(modelType, action.transform(input));
                }
            })
            .withProjection(new UnmanagedModelProjection<C>(modelType))
            .build();
    }

    public static <C> ModelRegistration unmanaged(ModelRegistrations.Builder builder, Class<C> type, Factory<? extends C> initializer) {
        return unmanaged(builder, ModelType.of(type), initializer);
    }

    public static <C> ModelRegistration unmanaged(ModelRegistrations.Builder builder, Class<C> type, C c) {
        return unmanaged(builder, ModelType.of(type), Factories.constant(c));
    }

    private static <C> ModelRegistration unmanaged(ModelRegistrations.Builder builder, final ModelType<C> modelType, final Factory<? extends C> initializer) {
        return builder.action(ModelActionRole.Create,
            new Action<MutableModelNode>() {
                @Override
                public void execute(MutableModelNode mutableModelNode) {
                    mutableModelNode.setPrivateData(modelType, initializer.create());
                }
            })
            .withProjection(UnmanagedModelProjection.of(modelType))
            .build();
    }

    public static <C> ModelRegistration unmanagedNode(ModelRegistrations.Builder builder, Class<C> modelType, Action<? super MutableModelNode> action) {
        return unmanagedNode(builder, ModelType.of(modelType), action);
    }

    public static <C> ModelRegistration unmanagedNode(ModelRegistrations.Builder builder, ModelType<C> modelType, Action<? super MutableModelNode> action) {
        return builder.action(ModelActionRole.Create, action)
            .withProjection(new UnmanagedModelProjection<C>(modelType))
            .build();
    }

    public static <C> ModelRegistration unmanaged(ModelRegistrations.Builder builder, C c) {
        return unmanaged(builder, c, Actions.doNothing());
    }

    public static <C> ModelRegistration unmanaged(ModelRegistrations.Builder builder, final C c, final Action<? super C> action) {
        return unmanaged(builder, ModelType.typeOf(c).getConcreteClass(), new Factory<C>() {
            @Override
            public C create() {
                action.execute(c);
                return c;
            }
        });
    }

    public static ModelRegistration asUnmanaged(ModelRegistrations.Builder builder, Class<?> type) {
        return builder.withProjection(UnmanagedModelProjection.of(type)).build();
    }

    public static <I> ModelRegistration modelMap(ModelRegistrations.Builder builder, final Class<I> itemType) {
        final ModelType<RuleAwarePolymorphicNamedEntityInstantiator<I>> instantiatorType = ModelRegistryHelper.instantiatorType(itemType);

        ModelType<I> modelType = ModelType.of(itemType);
        return builder.action(ModelActionRole.Create,
            new Action<MutableModelNode>() {
                @Override
                public void execute(MutableModelNode mutableModelNode) {
                    RuleAwarePolymorphicNamedEntityInstantiator<I> instantiator = new DefaultRuleAwarePolymorphicNamedEntityInstantiator<I>(
                        new DefaultPolymorphicNamedEntityInstantiator<I>(itemType, "this collection")
                    );
                    mutableModelNode.setPrivateData(instantiatorType, instantiator);
                }
            })
            .withProjection(ModelMapModelProjection.unmanaged(
                modelType,
                ChildNodeInitializerStrategyAccessors.of(NodeBackedModelMap.createUsingParentNode(modelType)))
            )
            .withProjection(UnmanagedModelProjection.of(instantiatorType))
            .build();
    }

    // MutableModelNode methods

    public static void applyToSelf(MutableModelNode node, ModelActionRole role, Transformer<ModelAction, ModelActionBuilder<?>> action) {
        node.applyToSelf(role, action.transform(ModelActionBuilder.of()));
    }

    public static void applyToLink(MutableModelNode node, ModelActionRole role, Transformer<ModelAction, ModelActionBuilder<?>> action) {
        node.applyToLink(role, action.transform(ModelActionBuilder.of()));
    }

    public static void applyTo(MutableModelNode node, NodePredicate predicate, ModelActionRole role, Transformer<? extends ModelAction, ? super ModelActionBuilder<?>> definition) {
        node.applyTo(predicate, role, definition.transform(ModelActionBuilder.of()));
    }

    public static void addLink(MutableModelNode node, String path, Transformer<ModelRegistration, ModelRegistrations.Builder> definition) {
        addLink(node, ModelPath.path(path), definition);
    }

    public static void addLink(MutableModelNode node, ModelPath path, Transformer<ModelRegistration, ModelRegistrations.Builder> definition) {
        node.addLink(definition.transform(registration(path)));
    }

    public static void addReference(MutableModelNode node, String name, Class<?> type, MutableModelNode target) {
        node.addReference(name, ModelType.of(type), target, new SimpleModelRuleDescriptor("<test>"));
    }

    public static void addLinkInstance(MutableModelNode node, String path, Object instance) {
        addLinkInstance(node, ModelPath.path(path), instance);
    }

    public static void addLinkInstance(MutableModelNode node, ModelPath path, Object instance) {
        node.addLink(unmanaged(registration(path), instance));
    }
}
