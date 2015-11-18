package org.gradle.model.internal.fixture;

import org.gradle.api.Action;
import org.gradle.api.NamedDomainObjectFactory;
import org.gradle.api.Nullable;
import org.gradle.api.Transformer;
import org.gradle.api.internal.PolymorphicNamedEntityInstantiator;
import org.gradle.api.internal.rules.RuleAwarePolymorphicNamedEntityInstantiator;
import org.gradle.internal.BiAction;
import org.gradle.model.ModelMap;
import org.gradle.model.RuleSource;
import org.gradle.model.internal.core.*;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;
import org.gradle.model.internal.core.rule.describe.SimpleModelRuleDescriptor;
import org.gradle.model.internal.registry.ModelRegistry;
import org.gradle.model.internal.type.ModelType;
import org.gradle.model.internal.type.ModelTypes;

import java.util.List;
import java.util.Set;

import static org.gradle.model.internal.core.ModelActionRole.Initialize;
import static org.gradle.model.internal.core.ModelActionRole.Mutate;

/**
 * A helper for adding rules to a model registry.
 *
 * Allows unsafe use of the model registry by allow registering of rules that can close over external, unmanaged, state.
 */
@SuppressWarnings("UnusedParameters")
public class ModelRegistryHelperExtension {
    public static ModelRuleDescriptor desc(ModelRegistry modelRegistry, String p) {
        return new SimpleModelRuleDescriptor(p);
    }

    public static ModelPath path(ModelRegistry modelRegistry, String p) {
        return ModelPath.path(p);
    }

    public static MutableModelNode atState(ModelRegistry modelRegistry, String path, ModelNode.State state) {
        return (MutableModelNode) modelRegistry.atState(ModelPath.path(path), state);
    }

    public static ModelNode.State state(ModelRegistry modelRegistry, String path) {
        return modelRegistry.state(ModelPath.path(path));
    }

    public static ModelActionBuilder<Object> action(ModelRegistry modelRegistry) {
        return ModelActionBuilder.of();
    }

    public static <C> ModelRegistry registerInstance(ModelRegistry modelRegistry, String path, final C c) {
        return modelRegistry.register(instanceRegistration(modelRegistry, path, c));
    }

    public static <C> ModelRegistry register(ModelRegistry modelRegistry, String path, final C c, Action<? super C> action) {
        return modelRegistry.register(registration(modelRegistry, path).unmanaged(c, action));
    }

    @Nullable
    public static MutableModelNode node(ModelRegistry modelRegistry, String path) {
        return modelRegistry.node(ModelPath.path(path));
    }

    public static ModelRegistry register(ModelRegistry modelRegistry, String path, Transformer<? extends ModelRegistration, ? super ModelRegistrationBuilder> definition) {
        return register(modelRegistry, ModelPath.path(path), definition);
    }

    public static ModelRegistry register(ModelRegistry modelRegistry, ModelPath path, Transformer<? extends ModelRegistration, ? super ModelRegistrationBuilder> definition) {
        return modelRegistry.register(definition.transform(registration(modelRegistry, path)));
    }

    public static <I> ModelRegistry modelMap(ModelRegistry modelRegistry, String path, final Class<I> itemType, final Action<? super PolymorphicNamedEntityInstantiator<I>> registrations) {
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
        return register(modelRegistry, path, new Transformer<ModelRegistration, ModelRegistrationBuilder>() {
            @Override
            public ModelRegistration transform(ModelRegistrationBuilder modelRegistrationBuilder) {
                return modelRegistrationBuilder.modelMap(itemType);
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

    public static <C> ModelRegistration registration(ModelRegistry modelRegistry, String path, C c, Action<C> action) {
        return registration(modelRegistry, path).unmanaged(c, action);
    }

    public static ModelRegistration registration(ModelRegistry modelRegistry, String path, Transformer<? extends ModelRegistration, ? super ModelRegistrationBuilder> action) {
        return action.transform(registration(modelRegistry, ModelPath.path(path)));
    }

    public static <C> ModelRegistration instanceRegistration(ModelRegistry modelRegistry, String path, final C c) {
        return registration(modelRegistry, ModelPath.path(path)).unmanaged(c);
    }

    public static ModelRegistrationBuilder registration(ModelRegistry modelRegistry, String path) {
        return registration(modelRegistry, ModelPath.path(path));
    }

    public static ModelRegistrationBuilder registration(ModelRegistry modelRegistry, ModelPath path) {
        return new ModelRegistrationBuilder(path);
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

    public static <C> ModelRegistration registration(ModelRegistry modelRegistry, String path, Class<C> type, String inputPath, final Transformer<? extends C, Object> action) {
        return registration(modelRegistry, path, ModelType.of(type), inputPath, action);
    }

    public static <C> ModelRegistration registration(ModelRegistry modelRegistry, String path, final ModelType<C> modelType, String inputPath, final Transformer<? extends C, Object> action) {
        return ModelRegistrations.of(ModelPath.path(path), ModelReference.of(inputPath), new BiAction<MutableModelNode, List<ModelView<?>>>() {
            @Override
            public void execute(MutableModelNode mutableModelNode, List<ModelView<?>> inputs) {
                mutableModelNode.setPrivateData(modelType, action.transform(inputs.get(0).getInstance()));
            }

        }).withProjection(new UnmanagedModelProjection<C>(modelType, true, true)).descriptor("create " + path).build();
    }
}
