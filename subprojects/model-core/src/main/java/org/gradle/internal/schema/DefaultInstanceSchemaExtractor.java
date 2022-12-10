/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.internal.schema;

import com.google.common.collect.ImmutableMap;
import org.gradle.internal.properties.annotations.PropertyMetadata;
import org.gradle.internal.properties.annotations.TypeMetadata;
import org.gradle.internal.properties.annotations.TypeMetadataStore;
import org.gradle.internal.properties.annotations.TypeMetadataWalker;
import org.gradle.internal.reflect.validation.TypeValidationContext;

import javax.annotation.Nullable;
import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.function.Function;
import java.util.function.Supplier;

public class DefaultInstanceSchemaExtractor<T, S extends InstanceSchema, B extends InstanceSchema.Builder<? extends S>> implements InstanceSchemaExtractor<T, S> {

    private final TypeMetadataWalker.InstanceMetadataWalker walker;
    private final Supplier<? extends B> builderFactory;
    private final Class<? extends Annotation> optionalAnnotation;
    private final ImmutableMap<Class<? extends Annotation>, ? extends PropertySchemaExtractor<? super B>> propertyExtractors;

    public DefaultInstanceSchemaExtractor(
        TypeMetadataStore typeMetadataStore,
        Class<? extends Annotation> nestedAnnotation,
        Class<? extends Annotation> optionalAnnotation,
        Supplier<? extends B> builderFactory,
        Collection<? extends PropertySchemaExtractor<? super B>> propertyExtractors
    ) {
        this.builderFactory = builderFactory;
        this.optionalAnnotation = optionalAnnotation;
        this.walker = TypeMetadataWalker.instanceWalker(typeMetadataStore, nestedAnnotation);
        this.propertyExtractors = propertyExtractors.stream()
            .collect(ImmutableMap.toImmutableMap(PropertySchemaExtractor::getAnnotationType, Function.identity()));
    }

    @Override
    public S extractSchema(Object instance, TypeValidationContext validationContext) {
        B builder = builderFactory.get();
        walker.walk(instance, new PropertySchemaCollectingVisitor(builder, validationContext));
        return builder.build();
    }

    private class PropertySchemaCollectingVisitor implements TypeMetadataWalker.InstanceMetadataVisitor {
        private final B builder;
        private final TypeValidationContext validationContext;

        public PropertySchemaCollectingVisitor(B builder, TypeValidationContext validationContext) {
            this.builder = builder;
            this.validationContext = validationContext;
        }

        @Override
        public void visitRoot(TypeMetadata typeMetadata, Object value) {
            typeMetadata.visitValidationFailures(null, validationContext);
        }

        @Override
        public void visitNested(TypeMetadata typeMetadata, String qualifiedName, PropertyMetadata propertyMetadata, @Nullable Object value) {
            typeMetadata.visitValidationFailures(qualifiedName, validationContext);
            builder.add(new DefaultNestedPropertySchema(qualifiedName, propertyMetadata, isOptional(propertyMetadata), value));
        }

        @Override
        public void visitLeaf(Object parent, String qualifiedName, PropertyMetadata propertyMetadata) {
            Class<? extends Annotation> propertyType = propertyMetadata.getPropertyType();
            PropertySchemaExtractor<? super B> propertySchemaExtractor = propertyExtractors.get(propertyType);
            if (propertySchemaExtractor == null) {
                throw new IllegalStateException("Property type not recognized: @" + propertyType.getSimpleName());
            }
            propertySchemaExtractor.extractProperty(qualifiedName, propertyMetadata, parent, builder);
        }

        @Override
        public void visitNestedUnpackingError(String qualifiedName, Exception e) {
            // TODO How do we handle this?
        }

        private boolean isOptional(PropertyMetadata propertyMetadata) {
            // TODO Do this with less redundancy somehow
            return propertyMetadata.isAnnotationPresent(optionalAnnotation);
        }
    }

    private static class DefaultNestedPropertySchema extends AbstractPropertySchema implements NestedPropertySchema {
        private final Object value;

        public DefaultNestedPropertySchema(String qualifiedName, PropertyMetadata metadata, boolean optional, @Nullable Object value) {
            super(qualifiedName, metadata, optional);
            this.value = value;
        }

        @Nullable
        @Override
        public Object getValue() {
            return value;
        }
    }
}
