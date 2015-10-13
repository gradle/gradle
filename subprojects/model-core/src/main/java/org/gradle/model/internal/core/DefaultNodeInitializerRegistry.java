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

import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;

public class DefaultNodeInitializerRegistry implements NodeInitializerRegistry {
    public static final ModelReference<NodeInitializerRegistry> DEFAULT_REFERENCE = ModelReference.of("nodeInitializerRegistry", NodeInitializerRegistry.class);

    private final List<NodeInitializerExtractionStrategy> allStrategies;
    private final List<NodeInitializerExtractionStrategy> additionalStrategies;
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
        this.allStrategies = Lists.newArrayList(
            modelSetNodeInitializerExtractionStrategy,
            managedSetNodeInitializerExtractionStrategy,
            modelMapNodeInitializerExtractionStrategy,
            scalarCollectionNodeInitializerExtractionStrategy,
            new ManagedImplStructNodeInitializerExtractionStrategy(schemaStore)
        );
        additionalStrategies = Lists.newArrayList();
    }


    private <T> NodeInitializer getNodeInitializer(ModelSchema<T> schema) {
        for (NodeInitializerExtractionStrategy extractor : allStrategies) {
            NodeInitializer nodeInitializer = extractor.extractNodeInitializer(schema);
            if (nodeInitializer != null) {
                return nodeInitializer;
            }
        }
        throw canNotConstructTypeException(schema.getType());
    }

    public <T> ModelTypeInitializationException canNotConstructTypeException(ModelType<T> type) {
        Iterable<ModelType<?>> scalars = Iterables.concat(ScalarTypes.TYPES, ScalarTypes.NON_FINAL_TYPES);
        Iterable<ModelType<?>> managedCollectionTypes = Iterables.concat(
            modelMapNodeInitializerExtractionStrategy.supportedTypes(),
            managedSetNodeInitializerExtractionStrategy.supportedTypes(),
            modelSetNodeInitializerExtractionStrategy.supportedTypes()
        );

        TreeSet<ModelType<?>> constructableTypes = new TreeSet<ModelType<?>>(new Comparator<ModelType<?>>() {
            @Override
            public int compare(ModelType<?> o1, ModelType<?> o2) {
                return o1.getSimpleName().compareTo(o2.getSimpleName());
            }
        });
        for (NodeInitializerExtractionStrategy extractor : additionalStrategies) {
            Iterables.addAll(constructableTypes, extractor.supportedTypes());
        }
        return new ModelTypeInitializationException(type, scalars, scalarCollectionNodeInitializerExtractionStrategy.supportedTypes(), managedCollectionTypes, constructableTypes);
    }

    @Override
    public NodeInitializer getNodeInitializer(NodeInitializerContext nodeInitializerContext) {
        return getNodeInitializer(schemaStore.getSchema(nodeInitializerContext.getModelType()));
    }

    @Override
    public void ensureHasInitializer(NodeInitializerContext nodeInitializer) {
        getNodeInitializer(nodeInitializer);
    }

    @Override
    public void registerStrategy(NodeInitializerExtractionStrategy strategy) {
        allStrategies.add(0, strategy);
        additionalStrategies.add(0, strategy);
    }
}
