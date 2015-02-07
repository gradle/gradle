/*
 * Copyright 2014 the original author or authors.
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
import net.jcip.annotations.ThreadSafe;
import org.gradle.api.Action;
import org.gradle.internal.Factory;
import org.gradle.model.collection.ManagedSet;
import org.gradle.model.internal.manage.schema.ModelSchema;
import org.gradle.model.internal.manage.schema.cache.ModelSchemaCache;
import org.gradle.model.internal.type.ModelType;

import java.util.Collections;
import java.util.List;

@ThreadSafe
public class ManagedSetStrategy implements ModelSchemaExtractionStrategy {

    private static final ModelType<ManagedSet<?>> MANAGED_SET_MODEL_TYPE = new ModelType<ManagedSet<?>>() {
    };
    private final Factory<String> supportedTypeDescriptions;

    public ManagedSetStrategy(Factory<String> supportedTypeDescriptions) {
        this.supportedTypeDescriptions = supportedTypeDescriptions;
    }

    public <T> ModelSchemaExtractionResult<T> extract(ModelSchemaExtractionContext<T> extractionContext, final ModelSchemaCache cache) {
        ModelType<T> type = extractionContext.getType();
        if (MANAGED_SET_MODEL_TYPE.isAssignableFrom(type)) {
            if (!type.getRawClass().equals(ManagedSet.class)) {
                throw new InvalidManagedModelElementTypeException(extractionContext, String.format("subtyping %s is not supported", ManagedSet.class.getName()));
            }
            if (type.isHasWildcardTypeVariables()) {
                throw new InvalidManagedModelElementTypeException(extractionContext, String.format("type parameter of %s cannot be a wildcard", ManagedSet.class.getName()));
            }

            List<ModelType<?>> typeVariables = type.getTypeVariables();
            if (typeVariables.isEmpty()) {
                throw new InvalidManagedModelElementTypeException(extractionContext, String.format("type parameter of %s has to be specified", ManagedSet.class.getName()));
            }

            ModelType<?> elementType = typeVariables.get(0);

            if (MANAGED_SET_MODEL_TYPE.isAssignableFrom(elementType)) {
                throw new InvalidManagedModelElementTypeException(extractionContext, String.format("%1$s cannot be used as type parameter of %1$s", ManagedSet.class.getName()));
            }

            ModelSchema<T> schema = ModelSchema.collection(extractionContext.getType(), elementType);
            ModelSchemaExtractionContext<?> typeParamExtractionContext = extractionContext.child(elementType, "element type", new Action<ModelSchemaExtractionContext<?>>() {
                public void execute(ModelSchemaExtractionContext<?> context) {
                    ModelSchema<?> typeParamSchema = cache.get(context.getType());

                    if (!typeParamSchema.getKind().isManaged()) {
                        throw new InvalidManagedModelElementTypeException(context.getParent(), String.format(
                                "cannot create a managed set of type %s as it is an unmanaged type.%nSupported types:%n%s",
                                context.getType(), supportedTypeDescriptions.create()
                        ));
                    }
                }
            });
            return new ModelSchemaExtractionResult<T>(schema, ImmutableList.of(typeParamExtractionContext));
        } else {
            return null;
        }
    }

    public Iterable<String> getSupportedManagedTypes() {
        return Collections.singleton(MANAGED_SET_MODEL_TYPE + " of a managed type");
    }
}
