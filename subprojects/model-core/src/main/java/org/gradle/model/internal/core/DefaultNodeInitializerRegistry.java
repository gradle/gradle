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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import org.gradle.model.internal.manage.schema.ModelSchema;
import org.gradle.model.internal.manage.schema.ModelSchemaStore;
import org.gradle.model.internal.manage.schema.extract.*;
import org.gradle.model.internal.type.ModelType;

import java.util.Collections;
import java.util.List;

import static com.google.common.collect.Lists.newArrayList;

public class DefaultNodeInitializerRegistry implements NodeInitializerRegistry {
    private final ModelSchemaStore schemaStore;
    private final ImmutableList<NodeInitializerExtractionStrategy> strategies;

    public DefaultNodeInitializerRegistry(ModelSchemaStore schemaStore, ConstructableTypesRegistry constructableTypesRegistry) {
        this(schemaStore, new DefaultInstanceFactoryRegistry(), Collections.<NodeInitializerExtractionStrategy>emptyList(), constructableTypesRegistry);
    }

    public DefaultNodeInitializerRegistry(ModelSchemaStore schemaStore, InstanceFactoryRegistry instanceFactoryRegistry,
                                          List<NodeInitializerExtractionStrategy> strategies, ConstructableTypesRegistry constructableTypesRegistry) {
        this.schemaStore = schemaStore;
        this.strategies = ImmutableList.<NodeInitializerExtractionStrategy>builder()
            .addAll(strategies)
            .add(new FactoryBasedNodeInitializerExtractionStrategy(instanceFactoryRegistry))
            .add(new ModelSetNodeInitializerExtractionStrategy())
            .add(new ManagedSetNodeInitializerExtractionStrategy())
            .add(new ModelMapNodeInitializerExtractionStrategy())
            .add(new ScalarCollectionNodeInitializerExtractionStrategy())
            .add(new ManagedImplStructNodeInitializerExtractionStrategy(schemaStore))
            .add(constructableTypesRegistry)
            .build();
    }

    @Override
    public <T> NodeInitializer getNodeInitializer(ModelSchema<T> schema) {
        for (NodeInitializerExtractionStrategy extractor : strategies) {
            NodeInitializer nodeInitializer = extractor.extractNodeInitializer(schema, this);
            if (nodeInitializer != null) {
                return nodeInitializer;
            }
        }
        List<ModelType<?>> supportedTypes = newArrayList();
        for (NodeInitializerExtractionStrategy extractor : strategies) {
            Iterables.addAll(supportedTypes, extractor.supportedTypes());
        }
        throw new ModelTypeInitializationException(schema.getType(), supportedTypes);
    }

    @Override
    public <T> NodeInitializer getNodeInitializer(ModelType<T> type) {
        ModelSchema<T> schema = schemaStore.getSchema(type);
        return getNodeInitializer(schema);
    }
}
