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

import com.google.common.collect.ImmutableList;
import org.gradle.api.Action;
import org.gradle.model.internal.manage.schema.ModelProperty;
import org.gradle.model.internal.manage.schema.ModelSchema;
import org.gradle.model.internal.manage.schema.ScalarCollectionSchema;
import org.gradle.model.internal.manage.schema.UnmanagedImplStructSchema;
import org.gradle.model.internal.method.WeaklyTypeReferencingMethod;
import org.gradle.model.internal.type.ModelType;

import java.util.Collections;
import java.util.List;
import java.util.Set;

public class JavaUtilCollectionStrategy implements ModelSchemaExtractionStrategy {

    public final static List<Class<?>> TYPES = ImmutableList.<Class<?>>of(
        List.class,
        Set.class
    );

    @Override
    public <T> void extract(ModelSchemaExtractionContext<T> extractionContext) {
        ModelType<T> type = extractionContext.getType();
        Class<? super T> rawClass = type.getRawClass();
        List<ModelType<?>> typeVariables = type.getTypeVariables();
        if (TYPES.contains(rawClass)) {
            if (typeVariables.size() > 0 && ScalarTypes.isScalarType(typeVariables.get(0))) {
                extractionContext.found(createSchema(extractionContext, type, typeVariables.get(0)));
            } else {
                extractionContext.found(new UnmanagedImplStructSchema<T>(
                    type,
                    Collections.<ModelProperty<?>>emptySet(),
                    Collections.<WeaklyTypeReferencingMethod<?, ?>>emptySet(),
                    Collections.<ModelSchemaAspect>emptySet(),
                    false
                ));
            }
        }
    }

    private <T, E> ScalarCollectionSchema<T, E> createSchema(ModelSchemaExtractionContext<T> extractionContext, ModelType<T> type, ModelType<E> elementType) {
        final ScalarCollectionSchema<T, E> schema = new ScalarCollectionSchema<T, E>(type, elementType);
        extractionContext.child(elementType, "element type", new Action<ModelSchema<E>>() {
            @Override
            public void execute(ModelSchema<E> elementTypeSchema) {
                schema.setElementTypeSchema(elementTypeSchema);
            }
        });
        return schema;
    }
}
