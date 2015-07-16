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

import org.gradle.api.Action;
import org.gradle.api.Named;
import org.gradle.api.Nullable;
import org.gradle.internal.BiAction;
import org.gradle.model.internal.core.*;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;
import org.gradle.model.internal.core.rule.describe.NestedModelRuleDescriptor;
import org.gradle.model.internal.manage.schema.ModelSchema;
import org.gradle.model.internal.manage.schema.ModelStructSchema;
import org.gradle.model.internal.type.ModelType;

import java.util.Collections;
import java.util.List;

public class ManagedModelCreators {

    public static <T> ModelCreator creator(ModelRuleDescriptor descriptor, ModelPath path, ModelSchema<T> schema) {
        return creator(descriptor, path, schema, (ModelAction<T>) null);
    }

    public static <T> ModelCreator creator(ModelRuleDescriptor descriptor, ModelPath path, ModelSchema<T> schema, Action<? super T> initializer) {
        ModelReference<T> modelReference = ModelReference.of(path, schema.getType());
        ModelAction<T> modelAction = NoInputsModelAction.of(modelReference, descriptor, initializer);
        return creator(descriptor, path, schema, modelAction);
    }

    public static <T> ModelCreator creator(ModelRuleDescriptor descriptor, ModelPath path, ModelSchema<T> schema, List<ModelReference<?>> initializerInputs, BiAction<? super T, ? super List<ModelView<?>>> initializer) {
        ModelReference<T> modelReference = ModelReference.of(path, schema.getType());
        ModelAction<T> modelAction = InputUsingModelAction.of(modelReference, descriptor, initializerInputs, initializer);
        return creator(descriptor, path, schema, modelAction);
    }

    private static <T> ModelCreator creator(ModelRuleDescriptor descriptor, ModelPath path, ModelSchema<T> schema, @Nullable ModelAction<T> initializer) {
        ModelType<T> type = schema.getType();
        ModelCreators.Builder builder = ModelCreators
            .of(path, schema.getNodeInitializer())
            .descriptor(descriptor);

        if (schema.getKind() == ModelSchema.Kind.STRUCT && Named.class.isAssignableFrom(type.getRawClass())) {
            // Only initialize "name" child node if the schema has such a node. This is not the case
            // for a managed subtype of an unmanaged type that implements Named.
            if (((ModelStructSchema<T>) schema).getProperties().containsKey("name")) {
                builder.action(ModelActionRole.Initialize, new NamedInitializer(path, descriptor));
            }
        }
        if (initializer != null) {
            builder.action(ModelActionRole.Initialize, initializer);
        }

        return builder.build();
    }

    private static class NamedInitializer implements ModelAction<Object> {

        private final ModelPath modelPath;
        private final ModelRuleDescriptor parentDescriptor;

        public NamedInitializer(ModelPath modelPath, ModelRuleDescriptor parentDescriptor) {
            this.modelPath = modelPath;
            this.parentDescriptor = parentDescriptor;
        }

        @Override
        public ModelReference<Object> getSubject() {
            return ModelReference.of(modelPath);
        }

        @Override
        public void execute(MutableModelNode modelNode, Object object, List<ModelView<?>> inputs) {
            MutableModelNode nameLink = modelNode.getLink("name");
            if (nameLink == null) {
                throw new IllegalStateException("expected name node for " + modelNode.getPath());
            }
            nameLink.setPrivateData(ModelType.of(String.class), modelNode.getPath().getName());
        }

        @Override
        public List<ModelReference<?>> getInputs() {
            return Collections.emptyList();
        }

        @Override
        public ModelRuleDescriptor getDescriptor() {
            return new NestedModelRuleDescriptor(parentDescriptor, "<set name>");
        }
    }

}
