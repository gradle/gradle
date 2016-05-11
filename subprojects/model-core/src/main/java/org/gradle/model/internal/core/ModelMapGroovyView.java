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
import org.gradle.api.internal.ClosureBackedAction;
import org.gradle.model.ModelMap;

import static org.gradle.internal.Cast.uncheckedCast;

/**
 * Used as the superclass for views for types that extend {@link org.gradle.model.ModelMap}. Mixes in Groovy DSL support.
 */
// TODO - mix in Groovy support using bytecode decoration instead
// TODO - validate closure parameters to check they are within bounds
public abstract class ModelMapGroovyView<I> extends GroovyObjectSupport implements ModelMap<I> {
    @Override
    public String toString() {
        return getDisplayName();
    }

    public void create(String name, Closure<? super I> configAction) {
        create(name, new ClosureBackedAction<I>(configAction));
    }

    public <S extends I> void create(String name, Class<S> type, Closure<? super S> configAction) {
        create(name, type, new ClosureBackedAction<I>(configAction));
    }

    public void named(String name, Closure<? super I> configAction) {
        named(name, new ClosureBackedAction<I>(configAction));
    }

    public void all(Closure<? super I> configAction) {
        all(new ClosureBackedAction<I>(configAction));
    }

    public <S> void withType(Class<S> type, Closure<? super S> configAction) {
        withType(type, new ClosureBackedAction<S>(configAction));
    }

    public void beforeEach(Closure<? super I> configAction) {
        beforeEach(new ClosureBackedAction<I>(configAction));
    }

    public <S> void beforeEach(Class<S> type, Closure<? super S> configAction) {
        beforeEach(type, new ClosureBackedAction<S>(configAction));
    }

    public void afterEach(Closure<? super I> configAction) {
        afterEach(new ClosureBackedAction<I>(configAction));
    }

    public <S> void afterEach(Class<S> type, Closure<? super S> configAction) {
        afterEach(type, new ClosureBackedAction<S>(configAction));
    }

    @Override
    public Object getProperty(String property) {
        if (property.equals("name")) {
            return getName();
        }
        if (property.equals("displayName")) {
            return getDisplayName();
        }
        I element = get(property);
        if (element == null) {
            throw new MissingPropertyException(property, ModelMap.class);
        }
        return element;
    }

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

