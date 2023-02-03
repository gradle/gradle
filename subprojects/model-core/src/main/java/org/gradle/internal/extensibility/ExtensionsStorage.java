/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.internal.extensibility;

import org.gradle.api.Action;
import org.gradle.api.UnknownDomainObjectException;
import org.gradle.api.plugins.ExtensionsSchema;
import org.gradle.api.reflect.TypeOf;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static java.lang.String.format;
import static org.gradle.internal.Cast.uncheckedCast;

public class ExtensionsStorage {

    private final Map<String, ExtensionHolder> extensions = new LinkedHashMap<String, ExtensionHolder>();

    public <T> void add(TypeOf<T> publicType, String name, T extension) {
        if (hasExtension(name)) {
            throw new IllegalArgumentException(
                format("Cannot add extension with name '%s', as there is an extension already registered with that name.", name));
        }
        extensions.put(name, new ExtensionHolder<T>(name, publicType, extension));
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
            return extensionHolder.configure(action);
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

    private List<String> registeredExtensionTypeNames() {
        List<String> types = new ArrayList<String>(extensions.size());
        for (ExtensionHolder holder : extensions.values()) {
            types.add(holder.getPublicType().getSimpleName());
        }
        return types;
    }

    /**
     * Doesn't actually return anything. Always throws a {@link UnknownDomainObjectException}.
     * @return Nothing.
     */
    private UnknownDomainObjectException unknownExtensionException(final String name) {
        throw new UnknownDomainObjectException("Extension with name '" + name + "' does not exist. Currently registered extension names: " + extensions.keySet());
    }

    private static class ExtensionHolder<T> implements ExtensionsSchema.ExtensionSchema {
        private final String name;
        private final TypeOf<T> publicType;
        protected final T extension;

        private ExtensionHolder(String name, TypeOf<T> publicType, T extension) {
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

        public T get() {
            return extension;
        }

        public T configure(Action<? super T> action) {
            action.execute(extension);
            return extension;
        }
    }
}
