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
import com.google.common.collect.Sets;
import org.gradle.model.internal.manage.schema.ModelSchema;
import org.gradle.model.internal.manage.schema.ModelSchemaStore;
import org.gradle.model.internal.manage.schema.extract.*;
import org.gradle.model.internal.type.ModelType;

import java.util.HashSet;
import java.util.List;

public class DefaultNodeInitializerRegistry implements NodeInitializerRegistry {
    private final List<NodeInitializerExtractionStrategy> strategies;
    private final List<NodeInitializerExtractionStrategy> variableStrategies;
    private final ModelSchemaStore schemaStore;
    private final ScalarCollectionNodeInitializerExtractionStrategy scalarCollectionNodeInitializerExtractionStrategy;
    private final ManagedSetNodeInitializerExtractionStrategy managedSetNodeInitializerExtractionStrategy;
    private final ModelMapNodeInitializerExtractionStrategy modelMapNodeInitializerExtractionStrategy;
    private final ModelSetNodeInitializerExtractionStrategy modelSetNodeInitializerExtractionStrategy;

    public DefaultNodeInitializerRegistry(ModelSchemaStore schemaStore) {
        this.schemaStore = schemaStore;
        scalarCollectionNodeInitializerExtractionStrategy = new ScalarCollectionNodeInitializerExtractionStrategy();
        managedSetNodeInitializerExtractionStrategy = new ManagedSetNodeInitializerExtractionStrategy();
        modelMapNodeInitializerExtractionStrategy = new ModelMapNodeInitializerExtractionStrategy();
        modelSetNodeInitializerExtractionStrategy = new ModelSetNodeInitializerExtractionStrategy();
        this.strategies = Lists.newArrayList(
            modelSetNodeInitializerExtractionStrategy,
            managedSetNodeInitializerExtractionStrategy,
            modelMapNodeInitializerExtractionStrategy,
            scalarCollectionNodeInitializerExtractionStrategy,
            new ManagedImplStructNodeInitializerExtractionStrategy(schemaStore)
        );
        variableStrategies = Lists.newArrayList();
    }

    @Override
    public <T> NodeInitializer getNodeInitializer(ModelSchema<T> schema) {
        for (NodeInitializerExtractionStrategy extractor : strategies) {
            NodeInitializer nodeInitializer = extractor.extractNodeInitializer(schema);
            if (nodeInitializer != null) {
                return nodeInitializer;
            }
        }
        Iterable<ModelType<?>> scalars = Iterables.concat(ScalarTypes.TYPES, ScalarTypes.NON_FINAL_TYPES);
        Iterable<ModelType<?>> managedCollectionTypes = Iterables.concat(modelMapNodeInitializerExtractionStrategy.supportedTypes(), managedSetNodeInitializerExtractionStrategy.supportedTypes(), modelSetNodeInitializerExtractionStrategy.supportedTypes());

        HashSet<ModelType<?>> otherManagedTypes = Sets.newHashSet();
        for (NodeInitializerExtractionStrategy extractor : variableStrategies) {
            Iterables.addAll(otherManagedTypes, extractor.supportedTypes());
        }
        throw new ModelTypeInitializationException(schema.getType(), scalars, scalarCollectionNodeInitializerExtractionStrategy.supportedTypes(), managedCollectionTypes, otherManagedTypes);
    }

    @Override
    public <T> NodeInitializer getNodeInitializer(ModelType<T> type) {
        return getNodeInitializer(schemaStore.getSchema(type));
    }

    @Override
    public void registerStrategy(NodeInitializerExtractionStrategy strategy) {
        strategies.add(0, strategy);
        variableStrategies.add(0, strategy);
    }
}
