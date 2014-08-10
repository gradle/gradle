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

package org.gradle.model.collection;

import org.gradle.api.Action;
import org.gradle.api.Incubating;
import org.gradle.model.ModelViewClosedException;
import org.gradle.model.internal.core.ModelPath;
import org.gradle.model.internal.core.ModelType;
import org.gradle.model.internal.core.ModelView;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;

@Incubating
public class NamedItemCollectionBuilderModelView<T> implements ModelView<NamedItemCollectionBuilder<T>> {

    private final ModelType<NamedItemCollectionBuilder<T>> type;
    private final NamedItemCollectionBuilder<T> rawInstance;
    private final NamedItemCollectionBuilder<T> instance = new Decorator();
    private final ModelPath path;
    private final ModelRuleDescriptor ruleDescriptor;

    private boolean closed;

    public NamedItemCollectionBuilderModelView(ModelType<NamedItemCollectionBuilder<T>> type, NamedItemCollectionBuilder<T> rawInstance, ModelPath path, ModelRuleDescriptor ruleDescriptor) {
        this.type = type;
        this.rawInstance = rawInstance;
        this.path = path;
        this.ruleDescriptor = ruleDescriptor;
    }

    public ModelType<NamedItemCollectionBuilder<T>> getType() {
        return type;
    }

    public NamedItemCollectionBuilder<T> getInstance() {
        return instance;
    }

    public void close() {
        closed = true;
    }

    class Decorator implements NamedItemCollectionBuilder<T> {
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
                throw new ModelViewClosedException(path, type, ruleDescriptor);
            }
        }
    }
}
