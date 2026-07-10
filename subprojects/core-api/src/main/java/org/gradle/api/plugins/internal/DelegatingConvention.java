/*
 * Copyright 2025 the original author or authors.
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


package org.gradle.api.plugins.internal;

import org.gradle.api.Action;
import org.gradle.api.UnknownDomainObjectException;
import org.gradle.api.plugins.ExtensionContainer;
import org.gradle.api.plugins.ExtensionsSchema;
import org.gradle.api.plugins.ExtraPropertiesExtension;
import org.gradle.api.reflect.TypeOf;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Implementation of the custom {@code org.gradle.api.pliugns.Convention} interface that delegates all {@code ExtensionContainer} methods
 * to another {@code ExtensionContainer} implementation.
 *
 * This class is used in {@link org.gradle.initialization.DefaultLegacyTypesSupport} to create an instance of the runtime only convention interface.
 * This avoids the generation of this code by the {@code org.gradle.initialization.DefaultLegacyTypesSupport} class and makes it more readable.
 *
 * The generated Convention interface enables compatibility with plugins that were built against the Gradle versions older than 8.2.
 */
@NullMarked
public class DelegatingConvention implements ExtensionContainer {

    private final ExtensionContainer delegate;

    public DelegatingConvention(ExtensionContainer delegate) {
        this.delegate = delegate;
    }

    // Custom method from the convention interface
    @Override
    @Nullable
    public <T> T findByType(TypeOf<T> type) {
        return delegate.findByType(type);
    }

    @Override
    public <T> void add(Class<T> publicType, String name, T extension) {
        delegate.add(publicType, name, extension);
    }

    @Override
    public <T> void add(TypeOf<T> publicType, String name, T extension) {
        delegate.add(publicType, name, extension);
    }

    @Override
    public void add(String name, Object extension) {
        delegate.add(name, extension);
    }

    @Override
    public <T> T create(Class<T> publicType, String name, Class<? extends T> instanceType, Object... constructionArguments) {
        return delegate.create(publicType, name, instanceType, constructionArguments);
    }

    @Override
    public <T> T create(TypeOf<T> publicType, String name, Class<? extends T> instanceType, Object... constructionArguments) {
        return delegate.create(publicType, name, instanceType, constructionArguments);
    }

    @Override
    public <T> T create(String name, Class<T> type, Object... constructionArguments) {
        return delegate.create(name, type, constructionArguments);
    }

    @Override
    public ExtensionsSchema getExtensionsSchema() {
        return delegate.getExtensionsSchema();
    }

    @Override
    public <T> T getByType(Class<T> type) throws UnknownDomainObjectException {
        return delegate.getByType(type);
    }

    @Override
    public <T> T getByType(TypeOf<T> type) throws UnknownDomainObjectException {
        return delegate.getByType(type);
    }

    @Override
    @Nullable
    public <T> T findByType(Class<T> type) {
        return delegate.findByType(type);
    }

    @Override
    public Object getByName(String name) throws UnknownDomainObjectException {
        return delegate.getByName(name);
    }

    @Override
    @Nullable
    public Object findByName(String name) {
        return delegate.findByName(name);
    }

    @Override
    public <T> void configure(Class<T> type, Action<? super T> action) {
        delegate.configure(type, action);
    }

    @Override
    public <T> void configure(TypeOf<T> type, Action<? super T> action) {
        delegate.configure(type, action);
    }

    @Override
    public <T> void configure(String name, Action<? super T> action) {
        delegate.configure(name, action);
    }

    @Override
    public ExtraPropertiesExtension getExtraProperties() {
        return delegate.getExtraProperties();
    }

    @Nullable
    public <T> T findPlugin(Class<T> type) throws IllegalStateException {
        return null;
    }
}
