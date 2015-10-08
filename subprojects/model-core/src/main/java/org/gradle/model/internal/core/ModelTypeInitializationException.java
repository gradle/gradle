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

    public ModelTypeInitializationException(ModelType<?> type, Iterable<ModelType<?>> candidates) {
        super(toMessage(type, candidates));
    }

    private static String toMessage(ModelType<?> type, Iterable<ModelType<?>> types) {
        return String.format("The model element of type: '%s' can not be constructed. The type must be managed (@Managed) or one of the following types [%s]", type, describe(types));
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

