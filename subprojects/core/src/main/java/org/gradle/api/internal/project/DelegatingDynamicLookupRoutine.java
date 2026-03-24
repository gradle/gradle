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

package org.gradle.api.internal.project;

import groovy.lang.MissingPropertyException;
import org.gradle.internal.metaobject.DynamicInvokeResult;
import org.gradle.internal.metaobject.DynamicObject;
import org.jspecify.annotations.Nullable;

import java.util.Map;

/**
 * A pure-delegation wrapper around a {@link DynamicLookupRoutine}.
 *
 * <p>Subclasses can override individual methods to add behavior (e.g. violation reporting)
 * while preserving the delegate's semantics for all other methods.</p>
 *
 * <p>This class must be written in Java because the {@code invokeMethod} delegation must
 * pass the varargs array directly to the delegate. Kotlin's spread operator ({@code *args})
 * creates a new array copy, which breaks the dynamic call tracking infrastructure used by
 * {@link org.gradle.configuration.internal.DynamicCallContextTracker}.</p>
 */
public class DelegatingDynamicLookupRoutine implements DynamicLookupRoutine {
    private final DynamicLookupRoutine delegate;

    public DelegatingDynamicLookupRoutine(DynamicLookupRoutine delegate) {
        this.delegate = delegate;
    }

    @Override
    public @Nullable Object property(DynamicObject receiver, String propertyName) throws MissingPropertyException {
        return delegate.property(receiver, propertyName);
    }

    @Override
    public @Nullable Object findProperty(DynamicObject receiver, String propertyName) {
        return delegate.findProperty(receiver, propertyName);
    }

    @Override
    public void setProperty(DynamicObject receiver, String name, @Nullable Object value) {
        delegate.setProperty(receiver, name, value);
    }

    @Override
    public boolean hasProperty(DynamicObject receiver, String propertyName) {
        return delegate.hasProperty(receiver, propertyName);
    }

    @Override
    public @Nullable Map<String, ?> getProperties(DynamicObject receiver) {
        return delegate.getProperties(receiver);
    }

    @Override
    public @Nullable Object invokeMethod(DynamicObject receiver, String name, Object... args) {
        return delegate.invokeMethod(receiver, name, args);
    }

    @Override
    public DynamicInvokeResult tryGetProperty(DynamicObject receiver, String name) {
        return delegate.tryGetProperty(receiver, name);
    }
}
