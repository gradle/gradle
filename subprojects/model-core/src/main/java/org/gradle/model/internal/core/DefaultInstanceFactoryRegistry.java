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

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import org.gradle.internal.Cast;
import org.gradle.model.internal.type.ModelType;

import java.util.Map;

public class DefaultInstanceFactoryRegistry implements InstanceFactoryRegistry {
    private final Map<ModelType<?>, ModelReference<? extends InstanceFactory<?, String>>> factoryReferences = Maps.newLinkedHashMap();

    @Override
    public <T> ModelReference<InstanceFactory<? super T, String>> getFactory(ModelType<T> type) {
        return Cast.uncheckedCast(factoryReferences.get(type));
    }

    @Override
    public <T> void register(ModelType<T> type, ModelReference<? extends InstanceFactory<? super T, String>> factoryReference) {
        factoryReferences.put(type, factoryReference);
    }

    @Override
    public Iterable<ModelType<?>> supportedTypes() {
        return ImmutableSet.<ModelType<?>>of().asList();
    }
}
