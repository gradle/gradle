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
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import org.gradle.api.GradleException;
import org.gradle.api.Incubating;
import org.gradle.model.internal.type.ModelType;

/**
 * Thrown when a NodeInitializer can not be found for a given type or when the type is not managed and can not be constructed.
 */
@Incubating
public class ModelTypeInitializationException extends GradleException {

    public ModelTypeInitializationException(ModelType<?> type, Iterable<ModelType<?>> scalarTypes, Iterable<ModelType<?>> scalarCollectionTypes, Iterable<ModelType<?>> managedCollectionTypes, Iterable<ModelType<?>> managedTypes) {
        super(toMessage(type, scalarTypes, scalarCollectionTypes, managedCollectionTypes, managedTypes));
    }

    private static String toMessage(ModelType<?> type, Iterable<ModelType<?>> scalarTypes, Iterable<ModelType<?>> scalarCollectionTypes,
                                    Iterable<ModelType<?>> managedCollectionTypes, Iterable<ModelType<?>> managedTypes) {
        StringBuffer s = new StringBuffer(String.format("A model node of type: '%s' can not be constructed.\n"
            + "Its type must be one the following:\n"
            + "  - A supported scalar type (%s)\n"
            + "  - An enumerated type (Enum)\n"
            + "  - An explicitly managed type (i.e. annotated with @Managed)\n"
            + "  - An explicitly unmanaged type (i.e. annotated with @Unmanaged)\n"
            + "  - A scalar collection type (%s)\n"
            + "  - A managed collection type (%s)\n", type, describe(scalarTypes), describe(scalarCollectionTypes), describe(managedCollectionTypes)));

        if (!Iterables.isEmpty(managedTypes)) {
            s.append("  - A managed type:\n");
            for (ModelType<?> modelType : managedTypes) {
                s.append(String.format("    - %s", modelType.getName()));
            }
        }
        return s.toString();
    }

    private static String describe(Iterable<ModelType<?>> types) {
        return Joiner.on(", ").join(ImmutableSet.copyOf(Iterables.transform(types, new Function<ModelType<?>, String>() {
            @Override
            public String apply(ModelType<?> input) {
                return input.getSimpleName();
            }
        })));
    }
}

