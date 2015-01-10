/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.model.internal.inspect;

import org.gradle.internal.BiAction;
import org.gradle.model.internal.core.*;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;
import org.gradle.model.internal.manage.schema.ModelProperty;
import org.gradle.model.internal.manage.schema.ModelSchema;
import org.gradle.model.internal.manage.schema.ModelSchemaStore;
import org.gradle.model.internal.type.ModelType;

public class ManagedModelInitializer<T> implements BiAction<MutableModelNode, Inputs> {
    private static final BiAction<Object, Inputs> NO_OP = new BiAction<Object, Inputs>() {
        @Override
        public void execute(Object subject, Inputs inputs) {
        }
    };

    private final ModelSchema<T> modelSchema;
    private final ModelAction<T> initializer;
    private final ModelRuleDescriptor descriptor;
    private final ModelSchemaStore schemaStore;
    private final ModelCreatorFactory modelCreatorFactory;

    public ManagedModelInitializer(ModelRuleDescriptor descriptor, ModelSchema<T> modelSchema, ModelSchemaStore schemaStore, ModelCreatorFactory modelCreatorFactory, ModelAction<T> initializer) {
        this.descriptor = descriptor;
        this.schemaStore = schemaStore;
        this.modelCreatorFactory = modelCreatorFactory;
        this.modelSchema = modelSchema;
        this.initializer = initializer;
    }

    public void execute(MutableModelNode modelNode, Inputs inputs) {
        for (ModelProperty<?> property : modelSchema.getProperties().values()) {
            addPropertyLink(modelNode, property);
        }
        if (initializer != null) {
            modelNode.applyToSelf(ModelActionRole.Initialize, initializer);
        }
    }

    private <P> void addPropertyLink(MutableModelNode modelNode, ModelProperty<P> property) {
        ModelType<P> propertyType = property.getType();
        ModelSchema<P> propertySchema = schemaStore.getSchema(propertyType);

        if (propertySchema.getKind() == ModelSchema.Kind.STRUCT && !property.isWritable()) {
            ModelCreator creator = modelCreatorFactory.creator(descriptor, modelNode.getPath().child(property.getName()), propertySchema);
            modelNode.addLink(creator);
        } else if (propertySchema.getKind() == ModelSchema.Kind.COLLECTION && !property.isWritable()) {
            ModelCreator creator = modelCreatorFactory.creator(descriptor, modelNode.getPath().child(property.getName()), propertySchema);
            modelNode.addLink(creator);
        } else {
            ModelProjection projection = new UnmanagedModelProjection<P>(propertyType, true, true);
            ModelCreator creator = ModelCreators.of(ModelReference.of(modelNode.getPath().child(property.getName()), propertyType), NO_OP)
                    .withProjection(projection)
                    .descriptor(descriptor).build();
            modelNode.addLink(creator);
        }
    }
}
