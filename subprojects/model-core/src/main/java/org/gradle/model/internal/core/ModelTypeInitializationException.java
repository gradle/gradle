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
import org.gradle.api.Incubating;
import org.gradle.model.ModelMap;
import org.gradle.model.ModelSet;
import org.gradle.model.internal.manage.schema.*;
import org.gradle.model.internal.type.ModelType;

/**
 * Thrown when a NodeInitializer can not be found for a given type or when the type is not managed and can not be constructed.
 */
@Incubating
public class ModelTypeInitializationException extends GradleException {

    public ModelTypeInitializationException(NodeInitializerContext context,
                                            ModelSchemaStore schemaStore,
                                            Iterable<ModelType<?>> scalarTypes,
                                            Iterable<ModelType<?>> scalarCollectionTypes,
                                            Iterable<ModelType<?>> managedCollectionTypes,
                                            Iterable<ModelType<?>> constructableTypes) {
        super(toMessage(context, schemaStore, scalarTypes, scalarCollectionTypes, managedCollectionTypes, constructableTypes));
    }

    private static String toMessage(NodeInitializerContext context,
                                    ModelSchemaStore schemaStore,
                                    Iterable<ModelType<?>> scalarTypes,
                                    Iterable<ModelType<?>> scalarCollectionTypes,
                                    Iterable<ModelType<?>> managedCollectionTypes,
                                    Iterable<ModelType<?>> constructableTypes) {


        Optional<ModelProperty<?>> modelPropertyOptional = context.getModelProperty();
        StringBuffer s = new StringBuffer();
        if (modelPropertyOptional.isPresent()) {
            s.append(String.format("A model element of type: '%s' can not be constructed.%n", context.getContainingType().get().getName()));
            ModelProperty<?> modelProperty = modelPropertyOptional.get();
            if (isManagedCollection(modelProperty.getType())) {
                s.append(String.format("It's property '%s %s' is not a valid managed collection%n", modelProperty.getType().getName(), modelProperty.getName()));
                ModelCollectionSchema<?, ?> schema = (ModelCollectionSchema) schemaStore.getSchema(modelProperty.getType());
                s.append(String.format("A managed collection can not contain '%s's%n", schema.getElementType()));
                explainManagedCollections(s, 1, constructableTypes);
            } else if (isScalarCollection(modelProperty.getType(), schemaStore)) {
                ModelCollectionSchema<?, ?> schema = (ModelCollectionSchema) schemaStore.getSchema(modelProperty.getType());
                s.append(String.format("It's property '%s %s' is not a valid scalar collection%n", modelProperty.getType().getName(), modelProperty.getName()));
                s.append(String.format("A scalar collection can not contain '%s's%n", schema.getElementType()));
                s.append(explainScalarCollections(scalarTypes));
            } else {
                s.append(String.format("It's property '%s %s' can not be constructed%n", modelProperty.getType().getName(), modelProperty.getName()));
                s.append(String.format("It must be one of:%n"));
                s.append("    - A managed collection. ");
                explainManagedCollections(s, 1, constructableTypes);
                s.append(String.format("%n    - A scalar collection. %s%n    - %s", explainScalarCollections(scalarTypes), describeUnmanagedProperties()));
                maybeAppendConstructables(s, constructableTypes, 1);
            }
        } else {
            s.append(String.format("A model element of type: '%s' can not be constructed.%n", context.getModelType()));
            s.append(String.format("It must be one the following:\n"
                + "  - A supported scalar type (%s)\n"
                + "  - An enumerated type (Enum)\n"
                + "  - An explicitly managed type (i.e. annotated with @Managed)\n"
                + "  - An explicitly unmanaged property (i.e. annotated with @Unmanaged)\n"
                + "  - A scalar collection type (%s)\n"
                + "  - A managed collection type (%s)\n", context.getModelType(), describe(scalarTypes), describe(scalarCollectionTypes), describe(managedCollectionTypes)));

            maybeAppendConstructables(s, constructableTypes, 2);
        }
        return s.toString();
    }

    private static String describeUnmanagedProperties() {
        return "An unmanaged property (i.e. annotated with @Unmanaged)";
    }

    private static String explainScalarCollections(Iterable<ModelType<?>> scalarTypes) {
        return String.format("A valid scalar collection takes the form of List<T> or Set<T> where 'T' is one of (%s)", describe(scalarTypes));
    }

    private static String explainManagedCollections(StringBuffer s, int pad, Iterable<ModelType<?>> constructableTypes) {
        s.append(String.format("A valid managed collection takes the form of ModelSet<T> or ModelMap<T> where 'T' is:%n        - A managed type (annotated with @Managed)"));
        maybeAppendConstructables(s, constructableTypes, pad + 1);
        return s.toString();
    }

    private static void maybeAppendConstructables(StringBuffer s, Iterable<ModelType<?>> constructableTypes, int pad) {
        if (!Iterables.isEmpty(constructableTypes)) {
            String padding = pad(pad);
            s.append(String.format("%n%s- or a type which Gradle is capable of constructing:", padding));
            for (ModelType<?> modelType : constructableTypes) {
                s.append(String.format("%n    %s- %s", padding, modelType.getName()));
            }
        }
    }

    private static String pad(int padding) {
        return Strings.padStart("", padding * 4, ' ');
    }

    private static boolean isScalarCollection(ModelType<?> type, ModelSchemaStore schemaStore) {
        ModelSchema<?> schema = schemaStore.getSchema(type);
        return schema instanceof ScalarCollectionSchema;
    }

    private static String describe(Iterable<ModelType<?>> types) {
        return Joiner.on(", ").join(ImmutableSet.copyOf(Iterables.transform(types, new Function<ModelType<?>, String>() {
            @Override
            public String apply(ModelType<?> input) {
                return input.getSimpleName();
            }
        })));
    }

    private static boolean isManagedCollection(ModelType<?> type) {
        Class<?> concreteClass = type.getConcreteClass();
        return concreteClass.equals(ModelMap.class) || concreteClass.equals(ModelSet.class);
    }
}


