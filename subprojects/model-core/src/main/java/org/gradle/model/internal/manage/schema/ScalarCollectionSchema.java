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
package org.gradle.model.internal.manage.schema;

import org.gradle.internal.Cast;
import org.gradle.model.internal.core.MutableModelNode;
import org.gradle.model.internal.type.ModelType;

import java.util.Collection;

public class ScalarCollectionSchema<T, E> extends ModelCollectionSchema<T, E> {
    public ScalarCollectionSchema(ModelType<T> type, ModelType<E> elementType) {
        super(type, elementType);
    }

    public static void clear(MutableModelNode node) {
        node.setPrivateData(Collection.class, null);
    }

    public static <E> void set(MutableModelNode node, Collection<E> values) {
        node.setPrivateData(Collection.class, values);
    }

    public static <E> Collection<E> get(MutableModelNode node) {
        return Cast.uncheckedCast(node.getPrivateData(Collection.class));
    }

}
