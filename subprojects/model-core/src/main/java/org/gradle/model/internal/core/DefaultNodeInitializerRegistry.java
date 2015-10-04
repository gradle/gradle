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

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import org.gradle.model.internal.manage.schema.ModelSchema;
import org.gradle.model.internal.manage.schema.ModelSchemaStore;
import org.gradle.model.internal.manage.schema.extract.*;
import org.gradle.model.internal.type.ModelType;

import java.util.List;

import static com.google.common.collect.Lists.newArrayList;

public class DefaultNodeInitializerRegistry implements NodeInitializerRegistry {
    private final List<NodeInitializerExtractionStrategy> strategies;
    private final ModelSchemaStore schemaStore;

    public DefaultNodeInitializerRegistry(ModelSchemaStore schemaStore) {
        this.schemaStore = schemaStore;
        this.strategies = Lists.newArrayList(
            new ModelSetNodeInitializerExtractionStrategy(),
            new ManagedSetNodeInitializerExtractionStrategy(),
            new ModelMapNodeInitializerExtractionStrategy(),
            new ScalarCollectionNodeInitializerExtractionStrategy(),
            new ManagedImplStructNodeInitializerExtractionStrategy(schemaStore)
        );
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
        return getNodeInitializer(schemaStore.getSchema(type));
    }

    @Override
    public void registerStrategy(NodeInitializerExtractionStrategy strategy) {
        strategies.add(0, strategy);
    }
}
