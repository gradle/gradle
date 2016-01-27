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

package org.gradle.model.internal.manage.schema.extract;

import org.gradle.model.internal.core.NodeInitializer;
import org.gradle.model.internal.core.NodeInitializerContext;
import org.gradle.model.internal.manage.schema.CollectionSchema;
import org.gradle.model.internal.manage.schema.ModelSchema;

public abstract class CollectionNodeInitializerExtractionSupport implements NodeInitializerExtractionStrategy {
    @Override
    public <T> NodeInitializer extractNodeInitializer(ModelSchema<T> schema, NodeInitializerContext<T> context) {
        if (!(schema instanceof CollectionSchema)) {
            return null;
        }
        return extractNodeInitializer((CollectionSchema<T, ?>) schema, context);
    }

    protected abstract <T, E> NodeInitializer extractNodeInitializer(CollectionSchema<T, E> schema, NodeInitializerContext<T> context);
}
