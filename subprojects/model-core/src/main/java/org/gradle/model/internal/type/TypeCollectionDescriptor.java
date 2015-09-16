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

package org.gradle.model.internal.type;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableSet;

public class TypeCollectionDescriptor {
    private final Iterable<ModelType<?>> types;

    public TypeCollectionDescriptor(Iterable<ModelType<?>> types) {
        this.types = types;
    }

    @Override
    public String toString() {
        //TODO - ak we end up with lots and lots of `CustomComponent`'s in org.gradle.model.internal.core.DefaultInstanceFactoryRegistry.supportedTypes
        //See: ComponentModelIntegrationTest
        return Joiner.on(", ").join(ImmutableSet.copyOf(FluentIterable.from(types).transform(typeNames)));
    }

    static Function<ModelType<?>, String> typeNames = new Function<ModelType<?>, String>() {
        @Override
        public String apply(ModelType<?> input) {
            return input.getSimpleName();
        }
    };
}
