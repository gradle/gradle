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

import com.google.common.base.Optional;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;
import org.gradle.model.internal.type.ModelType;

public class TypedModelProjection<M> extends TypeCompatibilityModelProjectionSupport<M> {

    private final ModelViewFactory<M> viewFactory;

    public static <M> ModelProjection of(ModelType<M> type, ModelViewFactory<M> viewFactory) {
        return new TypedModelProjection<M>(type, viewFactory);
    }

    public TypedModelProjection(ModelType<M> type, ModelViewFactory<M> viewFactory) {
        super(type);
        this.viewFactory = viewFactory;
    }

    @Override
    public Optional<String> getValueDescription(MutableModelNode modelNodeInternal) {
        return Optional.absent();
    }

    @Override
    protected ModelView<M> toView(MutableModelNode modelNode, ModelRuleDescriptor ruleDescriptor, boolean writable) {
        return viewFactory.toView(modelNode, ruleDescriptor, writable);
    }
}
