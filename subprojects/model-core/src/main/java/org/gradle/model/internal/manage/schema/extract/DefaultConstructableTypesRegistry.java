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
import com.google.common.collect.Maps;
import org.gradle.model.internal.core.NodeInitializer;
import org.gradle.model.internal.core.NodeInitializerRegistry;
import org.gradle.model.internal.manage.schema.ModelSchema;
import org.gradle.model.internal.type.ModelType;

import java.util.Map;

public class DefaultConstructableTypesRegistry implements ConstructableTypesRegistry {
    private Map<ModelType<?>, NodeInitializer> mappings = Maps.newLinkedHashMap();

    @Override
    public <T> NodeInitializer extractNodeInitializer(ModelSchema<T> schema, NodeInitializerRegistry nodeInitializerRegistry) {
        return mappings.get(schema.getType());
    }

    @Override
    public Iterable<ModelType<?>> supportedTypes() {
        //Returning an empty for now because this class `DefaultConstructableTypesRegistry` is shared for the duration of a gradle process (GlobalScopeServices)
        //and tests which verify the supported types cannot rely on this.
        return ImmutableList.of();
    }

    @Override
    public <T> void registerConstructableType(ModelType<T> type, NodeInitializer nodeInitializer) {
        mappings.put(type, nodeInitializer);
    }
}
