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

import com.google.common.base.Optional;
import org.gradle.model.ModelElement;
import org.gradle.model.internal.core.InstanceModelView;
import org.gradle.model.internal.core.ModelView;
import org.gradle.model.internal.core.MutableModelNode;
import org.gradle.model.internal.core.TypeCompatibilityModelProjectionSupport;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;
import org.gradle.model.internal.type.ModelType;

public class ModelElementProjection extends TypeCompatibilityModelProjectionSupport<ModelElement> {
    private static final ModelType<ModelElement> MODEL_ELEMENT_MODEL_TYPE = ModelType.of(ModelElement.class);
    private final ModelType<?> publicType;

    public ModelElementProjection(ModelType<?> publicType) {
        super(MODEL_ELEMENT_MODEL_TYPE);
        this.publicType = publicType;
    }

    @Override
    protected ModelView<ModelElement> toView(final MutableModelNode modelNode, ModelRuleDescriptor ruleDescriptor, boolean writable) {
        return InstanceModelView.of(modelNode.getPath(), MODEL_ELEMENT_MODEL_TYPE, new ModelElement() {
            @Override
            public String toString() {
                return getDisplayName();
            }

            @Override
            public String getName() {
                return modelNode.getPath().getName();
            }

            @Override
            public String getDisplayName() {
                return publicType.getDisplayName() + " '" + modelNode.getPath() + "'";
            }
        });
    }

    @Override
    public Optional<String> getValueDescription(MutableModelNode mutableModelNode) {
        return Optional.absent();
    }
}
