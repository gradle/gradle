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

package org.gradle.model.internal.core;

import groovy.lang.Closure;
import groovy.lang.GroovyObjectSupport;
import groovy.lang.MissingMethodException;
import groovy.lang.MissingPropertyException;
import org.gradle.api.Action;
import org.gradle.api.Nullable;
import org.gradle.api.internal.ClosureBackedAction;
import org.gradle.model.ModelMap;
import org.gradle.model.RuleSource;

import java.util.Collection;
import java.util.Iterator;
import java.util.Set;

import static org.gradle.internal.Cast.uncheckedCast;

/**
 * Used as the superclass for views for types that extend {@link org.gradle.model.ModelMap}.
 */
// TODO - mix in Groovy support
public class ModelMapGroovyDecorator<I> extends GroovyObjectSupport implements ModelMap<I> {

    private final ModelMap<I> delegate;

    public static <T> ModelMap<T> wrap(ModelMap<T> delegate) {
        return new ModelMapGroovyDecorator<T>(delegate);
    }

    public ModelMapGroovyDecorator(ModelMap<I> delegate) {
        this.delegate = delegate;
    }

    @Override
    public <S> ModelMap<S> withType(Class<S> type) {
        return new ModelMapGroovyDecorator<S>(delegate.withType(type));
    }

    @Override
    public int size() {
        return delegate.size();
    }

    @Override
    public boolean isEmpty() {
        return delegate.isEmpty();
    }

    @Override
    @Nullable
    public I get(Object name) {
        return delegate.get(name);
    }

    @Override
    @Nullable
    public I get(String name) {
        return delegate.get(name);
    }

    @Override
    public boolean containsKey(Object name) {
        return delegate.containsKey(name);
    }

    @Override
    public boolean containsValue(Object item) {
        return delegate.containsValue(item);
    }

    @Override
    public Set<String> keySet() {
        return delegate.keySet();
    }

    @Override
    public void create(String name) {
        delegate.create(name);
    }

    @Override
    public void create(String name, Action<? super I> configAction) {
        delegate.create(name, configAction);
    }

    @Override
    public <S extends I> void create(String name, Class<S> type) {
        delegate.create(name, type);
    }

    @Override
    public <S extends I> void create(String name, Class<S> type, Action<? super S> configAction) {
        delegate.create(name, type, configAction);
    }

    @Override
    public void named(String name, Action<? super I> configAction) {
        delegate.named(name, configAction);
    }

    @Override
    public void named(String name, Class<? extends RuleSource> ruleSource) {
        delegate.named(name, ruleSource);
    }

    @Override
    public void beforeEach(Action<? super I> configAction) {
        delegate.beforeEach(configAction);
    }

    @Override
    public <S> void beforeEach(Class<S> type, Action<? super S> configAction) {
        delegate.beforeEach(type, configAction);
    }

    @Override
    public void all(Action<? super I> configAction) {
        delegate.all(configAction);
    }

    @Override
    public <S> void withType(Class<S> type, Action<? super S> configAction) {
        delegate.withType(type, configAction);
    }

    @Override
    public <S> void withType(Class<S> type, Class<? extends RuleSource> rules) {
        delegate.withType(type, rules);
    }

    @Override
    public void afterEach(Action<? super I> configAction) {
        delegate.afterEach(configAction);
    }

    @Override
    public <S> void afterEach(Class<S> type, Action<? super S> configAction) {
        delegate.afterEach(type, configAction);
    }

    @Override
    public String toString() {
        return delegate.toString();
    }

    @Override
    public Collection<I> values() {
        return delegate.values();
    }

    @Override
    public Iterator<I> iterator() {
        return delegate.iterator();
    }

    // TODO - mix this in and validate closure parameters
    public void create(String name, Closure<? super I> configAction) {
        create(name, new ClosureBackedAction<I>(configAction));
    }

    // TODO - mix this in and validate closure parameters
    public <S extends I> void create(String name, Class<S> type, Closure<? super S> configAction) {
        create(name, type, new ClosureBackedAction<I>(configAction));
    }

    // TODO - mix this in and validate closure parameters
    public void named(String name, Closure<? super I> configAction) {
        named(name, new ClosureBackedAction<I>(configAction));
    }

    // TODO - mix this in and validate closure parameters
    public void all(Closure<? super I> configAction) {
        all(new ClosureBackedAction<I>(configAction));
    }

    // TODO - mix this in and validate closure parameters
    public <S> void withType(Class<S> type, Closure<? super S> configAction) {
        withType(type, new ClosureBackedAction<S>(configAction));
    }

    // TODO - mix this in and validate closure parameters
    public void beforeEach(Closure<? super I> configAction) {
        beforeEach(new ClosureBackedAction<I>(configAction));
    }

    // TODO - mix this in and validate closure parameters
    public <S> void beforeEach(Class<S> type, Closure<? super S> configAction) {
        beforeEach(type, new ClosureBackedAction<S>(configAction));
    }

    // TODO - mix this in and validate closure parameters
    public void afterEach(Closure<? super I> configAction) {
        afterEach(new ClosureBackedAction<I>(configAction));
    }

    // TODO - mix this in and validate closure parameters
    public <S> void afterEach(Class<S> type, Closure<? super S> configAction) {
        afterEach(type, new ClosureBackedAction<S>(configAction));
    }

    // TODO - mix this in
    @Override
    public Object getProperty(String property) {
        I element = delegate.get(property);
        if (element == null) {
            throw new MissingPropertyException(property, ModelMap.class);
        }
        return element;
    }

    // TODO - mix this in and validate closure parameters
    public Void methodMissing(String name, Object argsObj) {
        Object[] args = (Object[]) argsObj;
        if (args.length == 1 && args[0] instanceof Class<?>) {
            Class<? extends I> itemType = uncheckedCast(args[0]);
            create(name, itemType);
        } else if (args.length == 2 && args[0] instanceof Class<?> && args[1] instanceof Closure<?>) {
            Class<? extends I> itemType = uncheckedCast(args[0]);
            Closure<? super I> closure = uncheckedCast(args[1]);
            create(name, itemType, closure);
        } else if (args.length == 1 && args[0] instanceof Closure<?>) {
            Closure<? super I> closure = uncheckedCast(args[0]);
            named(name, closure);
        } else {
            throw new MissingMethodException(name, ModelMap.class, args);
        }
        return null;
    }

}

