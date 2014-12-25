/*
 * Copyright 2013 the original author or authors.
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
import net.jcip.annotations.NotThreadSafe;
import org.gradle.api.Action;
import org.gradle.api.internal.ClosureBackedAction;
import org.gradle.model.ModelViewClosedException;
import org.gradle.model.collection.CollectionBuilder;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;
import org.gradle.model.internal.type.ModelType;

@NotThreadSafe
public class CollectionBuilderModelView<T> implements ModelView<CollectionBuilder<T>> {

    private final ModelType<CollectionBuilder<T>> type;
    private final CollectionBuilder<T> rawInstance;
    private final CollectionBuilder<T> instance;
    private final ModelRuleDescriptor ruleDescriptor;

    private boolean closed;

    public CollectionBuilderModelView(ModelType<CollectionBuilder<T>> type, CollectionBuilder<T> rawInstance, ModelRuleDescriptor ruleDescriptor) {
        this.type = type;
        this.rawInstance = rawInstance;
        this.ruleDescriptor = ruleDescriptor;
        this.instance = new Decorator();
    }

    public ModelType<CollectionBuilder<T>> getType() {
        return type;
    }

    public CollectionBuilder<T> getInstance() {
        return instance;
    }

    public void close() {
        closed = true;
    }

    // TODO - mix in Groovy support
    public class Decorator extends GroovyObjectSupport implements CollectionBuilder<T> {
        @Override
        public String toString() {
            return rawInstance.toString();
        }

        @Override
        public void create(String name) {
            assertNotClosed();
            rawInstance.create(name);
        }

        @Override
        public void create(String name, Action<? super T> configAction) {
            assertNotClosed();
            rawInstance.create(name, configAction);
        }

        @Override
        public <S extends T> void create(String name, Class<S> type) {
            assertNotClosed();
            rawInstance.create(name, type);
        }

        @Override
        public <S extends T> void create(String name, Class<S> type, Action<? super S> configAction) {
            assertNotClosed();
            rawInstance.create(name, type, configAction);
        }

        @Override
        public void named(String name, Action<? super T> configAction) {
            assertNotClosed();
            rawInstance.named(name, configAction);
        }

        @Override
        public void all(Action<? super T> configAction) {
            assertNotClosed();
            rawInstance.all(configAction);
        }

        @Override
        public void finalizeAll(Action<? super T> configAction) {
            assertNotClosed();
            rawInstance.finalizeAll(configAction);
        }

        // TODO - mix this in
        public void create(String name, Closure<? super T> configAction) {
            create(name, new ClosureBackedAction<T>(configAction));
        }

        // TODO - mix this in
        public <S extends T> void create(String name, Class<S> type, Closure<? super S> configAction) {
            create(name, type, new ClosureBackedAction<T>(configAction));
        }

        // TODO - mix this in
        public void named(String name, Closure<? super T> configAction) {
            named(name, new ClosureBackedAction<T>(configAction));
        }

        // TODO - mix this in
        public void all(Closure<? super T> configAction) {
            all(new ClosureBackedAction<T>(configAction));
        }

        // TODO - mix this in
        public void finalizeAll(Closure<? super T> configAction) {
            finalizeAll(new ClosureBackedAction<T>(configAction));
        }

        // TODO - mix this in
        public Void methodMissing(String name, Object argsObj) {
            Object[] args = (Object[]) argsObj;
            if (args.length == 1 && args[0] instanceof Class<?>) {
                @SuppressWarnings("unchecked")
                Class<? extends T> itemType = (Class<? extends T>) args[0];
                create(name, itemType);
            } else if (args.length == 2 && args[0] instanceof Class<?> && args[1] instanceof Closure<?>) {
                @SuppressWarnings("unchecked")
                Class<? extends T> itemType = (Class<? extends T>) args[0];
                @SuppressWarnings("unchecked")
                Closure<? super T> closure = (Closure<T>) args[1];
                create(name, itemType, closure);
            } else if (args.length == 1 && args[0] instanceof Closure<?>) {
                @SuppressWarnings("unchecked")
                Closure<? super T> closure = (Closure<? super T>) args[0];
                named(name, closure);
            } else {
                throw new MissingMethodException(name, getClass(), args);
            }
            return null;
        }

        private void assertNotClosed() {
            if (closed) {
                throw new ModelViewClosedException(type, ruleDescriptor);
            }
        }
    }
}
