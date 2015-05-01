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
import org.gradle.model.ModelViewClosedException;
import org.gradle.model.collection.CollectionBuilder;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;
import org.gradle.model.internal.type.ModelType;

@NotThreadSafe
public class CollectionBuilderModelView<T> implements ModelView<CollectionBuilder<T>>, ModelViewState {

    private final ModelType<CollectionBuilder<T>> type;
    private final CollectionBuilder<T> instance;
    private final ModelRuleDescriptor ruleDescriptor;
    private final ModelPath path;

    private boolean closed;

    public CollectionBuilderModelView(ModelPath path, ModelType<CollectionBuilder<T>> type, CollectionBuilder<T> rawInstance, ModelRuleDescriptor ruleDescriptor) {
        this.path = path;
        this.type = type;
        this.ruleDescriptor = ruleDescriptor;
        this.instance = new CollectionBuilderGroovyDecorator<T>(rawInstance, this);
    }

    @Override
    public ModelPath getPath() {
        return path;
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

    @Override
    public void assertCanMutate() {
        if (closed) {
            throw new ModelViewClosedException(type, ruleDescriptor);
        }
    }
}
