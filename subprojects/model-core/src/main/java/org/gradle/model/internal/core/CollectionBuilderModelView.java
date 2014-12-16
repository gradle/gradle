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

import net.jcip.annotations.NotThreadSafe;
import org.gradle.api.Action;
import org.gradle.internal.Cast;
import org.gradle.internal.reflect.Instantiator;
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

    public CollectionBuilderModelView(Instantiator instantiator, ModelType<CollectionBuilder<T>> type, CollectionBuilder<T> rawInstance, ModelRuleDescriptor ruleDescriptor) {
        this.type = type;
        this.rawInstance = rawInstance;
        this.ruleDescriptor = ruleDescriptor;
        this.instance = Cast.uncheckedCast(instantiator.newInstance(Decorator.class, this));
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

    public class Decorator implements CollectionBuilder<T> {
        public void create(String name) {
            assertNotClosed();
            rawInstance.create(name);
        }

        public void create(String name, Action<? super T> configAction) {
            assertNotClosed();
            rawInstance.create(name, configAction);
        }

        public <S extends T> void create(String name, Class<S> type) {
            assertNotClosed();
            rawInstance.create(name, type);
        }

        public <S extends T> void create(String name, Class<S> type, Action<? super S> configAction) {
            assertNotClosed();
            rawInstance.create(name, type, configAction);
        }

        private void assertNotClosed() {
            if (closed) {
                throw new ModelViewClosedException(type, ruleDescriptor);
            }
        }
    }
}
