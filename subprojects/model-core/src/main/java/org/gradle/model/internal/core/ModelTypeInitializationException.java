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

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import org.gradle.api.GradleException;
import org.gradle.model.ModelMap;
import org.gradle.model.ModelSet;
import org.gradle.model.internal.manage.schema.CollectionSchema;
import org.gradle.model.internal.manage.schema.ModelSchemaStore;
import org.gradle.model.internal.type.ModelType;

import java.util.List;
import java.util.Set;

/**
 * Thrown when a NodeInitializer can not be found for a given type or when the type is not managed and can not be constructed.
 */
public class ModelTypeInitializationException extends GradleException {

    private static final String MANAGED_TYPE_DESCRIPTION = "A managed type (annotated with @Managed)";
    private static final String UNMANAGED_PROPERTY_DESCRIPTION = "An unmanaged property (i.e. annotated with @Unmanaged)";

    public ModelTypeInitializationException(NodeInitializerContext<?> context,
                                            ModelSchemaStore schemaStore,
                                            Iterable<? extends ModelType<?>> scalarTypes,
                                            Iterable<? extends ModelType<?>> constructibleTypes) {
        super(toMessage(context, schemaStore, scalarTypes, constructibleTypes));
    }

    private static <T> String toMessage(NodeInitializerContext<T> context,
                                    ModelSchemaStore schemaStore,
                                    Iterable<? extends ModelType<?>> scalarTypes,
                                    Iterable<? extends ModelType<?>> constructibleTypes) {

        Optional<? extends NodeInitializerContext.PropertyContext> propertyContextOptional = context.getPropertyContextOptional();
        StringBuilder s = new StringBuilder();
        if (propertyContextOptional.isPresent()) {
            NodeInitializerContext.PropertyContext propertyContext = propertyContextOptional.get();
            s.append(String.format("A model element of type: '%s' can not be constructed.%n", propertyContext.getDeclaringType().getName()));
            String propertyName = propertyContext.getName();
            ModelType<?> propertyType = propertyContext.getType();
            if (isManagedCollection(propertyType)) {
                s.append(String.format("Its property '%s %s' is not a valid managed collection%n", propertyType.getName(), propertyName));
                CollectionSchema<?, ?> schema = (CollectionSchema) schemaStore.getSchema(propertyType);
                s.append(String.format("A managed collection can not contain '%s's%n", schema.getElementType()));
                appendManagedCollections(s, 1, constructibleTypes);
            } else if (isScalarCollection(propertyType, propertyContext.isDeclaredAsHavingUnmanagedType())) {
                ModelType<?> innerType = propertyType.getTypeVariables().get(0);
                s.append(String.format("Its property '%s %s' is not a valid scalar collection%n", propertyType.getName(), propertyName));
                s.append(String.format("A scalar collection can not contain '%s's%n", innerType));
                s.append(explainScalarCollections(scalarTypes));
            } else {
                s.append(String.format("Its property '%s %s' can not be constructed%n", propertyType.getName(), propertyName));
                s.append(String.format("It must be one of:%n"));
                s.append(String.format("    - %s%n", MANAGED_TYPE_DESCRIPTION));
                s.append("    - A managed collection. ");
                appendManagedCollections(s, 1, constructibleTypes);
                s.append(String.format("%n    - A scalar collection. %s%n    - %s", explainScalarCollections(scalarTypes), UNMANAGED_PROPERTY_DESCRIPTION));
                maybeAppendConstructibles(s, constructibleTypes, 1);
            }
        } else {
            s.append(String.format("A model element of type: '%s' can not be constructed.%n", context.getModelType().getName()));
            s.append(String.format("It must be one of:%n"));
            s.append(String.format("    - %s", MANAGED_TYPE_DESCRIPTION));
            maybeAppendConstructibles(s, constructibleTypes, 1);
        }
        return s.toString();
    }

    private static String explainScalarCollections(Iterable<? extends ModelType<?>> scalarTypes) {
        return String.format("A valid scalar collection takes the form of List<T> or Set<T> where 'T' is one of (%s)", describe(scalarTypes));
    }

    private static String appendManagedCollections(StringBuilder s, int pad, Iterable<? extends ModelType<?>> constructibleTypes) {
        s.append(String.format("A valid managed collection takes the form of ModelSet<T> or ModelMap<T> where 'T' is:%n        - %s", MANAGED_TYPE_DESCRIPTION));
        maybeAppendConstructibles(s, constructibleTypes, pad + 1);
        return s.toString();
    }

    private static void maybeAppendConstructibles(StringBuilder s, Iterable<? extends ModelType<?>> constructibleTypes, int pad) {
        if (!Iterables.isEmpty(constructibleTypes)) {
            String padding = pad(pad);
            s.append(String.format("%n%s- or a type which Gradle is capable of constructing:", padding));
            for (ModelType<?> modelType : constructibleTypes) {
                s.append(String.format("%n    %s- %s", padding, modelType.getName()));
            }
        }
    }

    private static String pad(int padding) {
        return Strings.padStart("", padding * 4, ' ');
    }

    private static boolean isScalarCollection(ModelType<?> propertyType, boolean declaredAsHavingUnmanagedType) {
        Class<?> concreteClass = propertyType.getConcreteClass();
        return (concreteClass.equals(List.class) || concreteClass.equals(Set.class))
            && !declaredAsHavingUnmanagedType;
    }

    private static String describe(Iterable<? extends ModelType<?>> types) {
        return Joiner.on(", ").join(ImmutableSet.copyOf(Iterables.transform(types, new Function<ModelType<?>, String>() {
            @Override
            public String apply(ModelType<?> input) {
                return input.getDisplayName();
            }
        })));
    }

    private static boolean isManagedCollection(ModelType<?> type) {
        Class<?> concreteClass = type.getConcreteClass();
        return concreteClass.equals(ModelMap.class) || concreteClass.equals(ModelSet.class);
    }
}
