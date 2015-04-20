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

import org.gradle.model.collection.CollectionBuilder;
import org.gradle.model.internal.type.ModelType;

public class SpecializedCollectionBuilderView<P extends CollectionBuilder<E>, E> implements ModelView<P> {

    private final ModelPath path;
    private ModelType<P> modelType;
    private P instance;

    public SpecializedCollectionBuilderView(ModelPath path, ModelType<P> modelType, P decorate) {
        this.path = path;
        this.modelType = modelType;
        this.instance = decorate;
    }

    @Override
    public ModelPath getPath() {
        return path;
    }

    @Override
    public ModelType<P> getType() {
        return modelType;
    }

    @Override
    public P getInstance() {
        return instance;
    }

    @Override
    public void close() {
        ((ClosableCollectionBuilder<E>) instance).close();
    }
}
