/*
 * Copyright 2011 the original author or authors.
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

package org.gradle.api.internal.plugins;

import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.UnknownDomainObjectException;
import org.gradle.api.internal.provider.AbstractProvider;
import org.gradle.api.plugins.DeferredConfigurable;
import org.gradle.api.plugins.ExtensionContainer;
import org.gradle.api.plugins.ExtensionsSchema;
import org.gradle.api.reflect.HasPublicType;
import org.gradle.api.reflect.TypeOf;
import org.gradle.internal.ImmutableActionSet;
import org.gradle.internal.MutableActionSet;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.util.DeprecationLogger;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static java.lang.String.format;
import static org.gradle.internal.Cast.uncheckedCast;

public class ExtensionsStorage {

    private final Instantiator instantiator;
    private final Map<String, ExtensionHolder> extensions = new LinkedHashMap<String, ExtensionHolder>();

    public ExtensionsStorage(Instantiator instantiator) {
        this.instantiator = instantiator;
    }

    private Instantiator getInstantiator() {
        if (instantiator == null) {
            throw new GradleException("request for DefaultConvention.instantiator when the object was constructed without a convention");
        }
        return instantiator;
    }

    private <T> T instantiate(Class<? extends T> instanceType, Object[] constructionArguments) {
        return getInstantiator().newInstance(instanceType, constructionArguments);
    }

    public void add(String name, Object extension) {
        if (extension instanceof Class) {
            create(name, (Class<?>) extension);
        } else {
            addWithDefaultPublicType(extension.getClass(), name, extension);
        }
    }

    public <T> void add(TypeOf<T> publicType, String name, T extension) {
        if (hasExtension(name) && !(extensions.get(name) instanceof CreatingExtensionProvider)) {
            throw new IllegalArgumentException(
                format("Cannot add extension with name '%s', as there is an extension already registered with that name.", name));
        }
        extensions.put(name, wrap(name, publicType, extension));
    }

    public boolean hasExtension(String name) {
        return extensions.containsKey(name);
    }

    public Map<String, Object> getAsMap() {
        Map<String, Object> rawExtensions = new LinkedHashMap<String, Object>(extensions.size());
        for (Map.Entry<String, ExtensionHolder> entry : extensions.entrySet()) {
            rawExtensions.put(entry.getKey(), entry.getValue().get());
        }
        return rawExtensions;
    }

    public ExtensionsSchema getSchema() {
        return DefaultExtensionsSchema.create(extensions.values());
    }

    public <T> T configureExtension(String name, Action<? super T> action) {
        ExtensionHolder<T> extensionHolder = uncheckedCast(extensions.get(name));
        if (extensionHolder != null) {
            extensionHolder.configure(action);
            return extensionHolder.get();
        }
        throw unknownExtensionException(name);
    }

    public <T> void configureExtension(TypeOf<T> type, Action<? super T> action) {
        getHolderByType(type).configure(action);
    }

    public <T> T getByType(TypeOf<T> type) {
        return getHolderByType(type).get();
    }

    public <T> T findByType(TypeOf<T> type) {
        ExtensionHolder<T> found = findHolderByType(type);
        return found != null ? found.get() : null;
    }

    private <T> ExtensionHolder<T> getHolderByType(TypeOf<T> type) {
        ExtensionHolder<T> found = findHolderByType(type);
        if (found != null) {
            return found;
        }
        throw new UnknownDomainObjectException(
            "Extension of type '" + type.getSimpleName() + "' does not exist. Currently registered extension types: " + registeredExtensionTypeNames());
    }

    private <T> ExtensionHolder<T> findHolderByType(TypeOf<T> type) {
        ExtensionHolder<T> firstHolderWithExactPublicType = firstHolderWithExactPublicType(type);
        return firstHolderWithExactPublicType != null
            ? firstHolderWithExactPublicType
            : firstHolderWithAssignableType(type);
    }

    @Nullable
    private <T> ExtensionHolder<T> firstHolderWithExactPublicType(TypeOf<T> type) {
        for (ExtensionHolder extensionHolder : extensions.values()) {
            if (type.equals(extensionHolder.getPublicType())) {
                return uncheckedCast(extensionHolder);
            }
        }
        return null;
    }

    @Nullable
    private <T> ExtensionHolder<T> firstHolderWithAssignableType(TypeOf<T> type) {
        for (ExtensionHolder extensionHolder : extensions.values()) {
            if (type.isAssignableFrom(extensionHolder.getPublicType())) {
                return uncheckedCast(extensionHolder);
            }
        }
        return null;
    }

    public Object getByName(String name) {
        Object extension = findByName(name);
        if (extension != null) {
            return extension;
        }
        throw unknownExtensionException(name);
    }

    public Object findByName(String name) {
        ExtensionHolder extensionHolder = extensions.get(name);
        return extensionHolder != null ? extensionHolder.get() : null;
    }

    private <T> ExtensionHolder<T> wrap(String name, TypeOf<T> publicType, T extension) {
        if (isDeferredConfigurable(extension)) {
            if (!extension.getClass().getName().startsWith("org.gradle.api.publish.internal.DeferredConfigurablePublishingExtension")) {
                DeprecationLogger.nagUserOfDeprecated("@DeferredConfigurable");
            }
            return new DeferredConfigurableExtensionHolder<T>(name, publicType, extension);
        }
        return new ExistingExtensionProvider<T>(name, publicType, extension);
    }

    private List<String> registeredExtensionTypeNames() {
        List<String> types = new ArrayList<String>(extensions.size());
        for (ExtensionHolder holder : extensions.values()) {
            types.add(holder.getPublicType().getSimpleName());
        }
        return types;
    }

    private <T> boolean isDeferredConfigurable(T extension) {
        return extension.getClass().isAnnotationPresent(DeferredConfigurable.class);
    }

    /**
     * Doesn't actually return anything. Always throws a {@link UnknownDomainObjectException}.
     *
     * @return Nothing.
     */
    private UnknownDomainObjectException unknownExtensionException(final String name) {
        throw new UnknownDomainObjectException("Extension with name '" + name + "' does not exist. Currently registered extension names: " + extensions.keySet());
    }

    private void addWithDefaultPublicType(Class<?> defaultType, String name, Object extension) {
        add(preferredPublicTypeOf(extension, defaultType), name, extension);
    }

    private TypeOf<Object> preferredPublicTypeOf(Object extension, Class<?> defaultType) {
        if (extension instanceof HasPublicType) {
            return uncheckedCast(((HasPublicType) extension).getPublicType());
        }
        return TypeOf.<Object>typeOf(defaultType);
    }

    public <T> T create(String name, Class<T> instanceType, Object... constructionArguments) {
        T instance = instantiate(instanceType, constructionArguments);
        addWithDefaultPublicType(instanceType, name, instance);
        return instance;
    }

    public <T> T create(TypeOf<T> publicType, String name, Class<? extends T> instanceType, Object... constructionArguments) {
        T instance = instantiate(instanceType, constructionArguments);
        add(publicType, name, instance);
        return instance;
    }

    public <T> ExtensionContainer.ExtensionProvider<T> register(Class<T> publicType, String name, Class<? extends T> instanceType, Object[] constructionArguments) {
        ExtensionHolder<T> value = new CreatingExtensionProvider<T>(name, TypeOf.typeOf(publicType), instanceType, constructionArguments);
        extensions.put(name, value);
        return value;
    }

    public <T> ExtensionContainer.ExtensionProvider<T> withType(Class<T> publicType) {
        return firstHolderWithExactPublicType(TypeOf.typeOf(publicType));
    }

    private interface ExtensionHolder<T> extends ExtensionsSchema.ExtensionSchema, ExtensionContainer.ExtensionProvider<T> {
    }

    private static class ExistingExtensionProvider<T> extends AbstractProvider<T> implements ExtensionHolder<T> {
        private final String name;
        private final TypeOf<T> publicType;
        protected final T extension;

        private ExistingExtensionProvider(String name, TypeOf<T> publicType, T extension) {
            this.name = name;
            this.publicType = publicType;
            this.extension = extension;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public TypeOf<T> getPublicType() {
            return publicType;
        }

        @Override
        public boolean isDeferredConfigurable() {
            return false;
        }

        public T get() {
            return extension;
        }

        @Nullable
        @Override
        public T getOrNull() {
            return extension;
        }

        public void configure(Action<? super T> action) {
            action.execute(extension);
        }

        @Nullable
        @Override
        public Class<T> getType() {
            return getPublicType().rawClass();
        }
    }

    private class CreatingExtensionProvider<T> extends AbstractProvider<T> implements ExtensionHolder<T> {
        private final String name;
        private final TypeOf<T> publicType;
        private final Class<? extends T> implementationType;
        private Object[] constructorArgs;
        private ImmutableActionSet<T> whenCreated = ImmutableActionSet.empty();
        private T extension;
        private Throwable failure;

        private CreatingExtensionProvider(String name, TypeOf<T> publicType, Class<? extends T> implementationType, Object[] constructorArgs) {
            this.name = name;
            this.publicType = publicType;
            this.implementationType = implementationType;
            this.constructorArgs = constructorArgs;
        }

        @Nullable
        @Override
        public Class<T> getType() {
            return publicType.rawClass();
        }

        @Override
        public void configure(Action<? super T> configureAction) {
            if (extension == null) {
                whenCreated = whenCreated.add(configureAction);
            } else {
                configureAction.execute(extension);
            }
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public TypeOf<?> getPublicType() {
            return publicType;
        }

        @Override
        public boolean isDeferredConfigurable() {
            return false;
        }

        @Nullable
        @Override
        public T getOrNull() {
            if (failure != null) {
                rethrowFailure();
            }
            if (extension == null) {
                try {
                    extension = create(publicType, name, implementationType, constructorArgs);
                    whenCreated.execute(extension);
                } catch (Throwable t) {
                    failure = t;
                    rethrowFailure();
                } finally {
                    constructorArgs = null;
                    whenCreated = null;
                }
            }
            return extension;
        }

        private void rethrowFailure() {
            throw new IllegalStateException("Could not create extension of type " + publicType, failure);
        }

        @Override
        public boolean isPresent() {
            return findHolderByType(publicType) != null;
        }
    }

    private static class DeferredConfigurableExtensionHolder<T> extends ExistingExtensionProvider<T> {
        private MutableActionSet<T> actions = new MutableActionSet<T>();
        private boolean configured;
        private Throwable configureFailure;

        DeferredConfigurableExtensionHolder(String name, TypeOf<T> publicType, T extension) {
            super(name, publicType, extension);
        }

        @Override
        public boolean isDeferredConfigurable() {
            return true;
        }

        @Override
        public T get() {
            configureNow();
            return extension;
        }

        @Override
        public void configure(Action<? super T> action) {
            configureLater(action);
        }

        private void configureLater(Action<? super T> action) {
            if (configured) {
                throw new InvalidUserDataException(format("Cannot configure the '%s' extension after it has been accessed.", getName()));
            }
            actions.add(action);
        }

        private void configureNow() {
            if (!configured) {
                configured = true;
                try {
                    actions.execute(extension);
                } catch (Throwable t) {
                    configureFailure = t;
                }
                actions = null;
            }

            if (configureFailure != null) {
                throw UncheckedException.throwAsUncheckedException(configureFailure);
            }
        }
    }
}
