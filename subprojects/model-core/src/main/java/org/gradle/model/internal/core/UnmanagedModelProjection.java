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

package org.gradle.model.internal.core;

import net.jcip.annotations.ThreadSafe;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;
import org.gradle.model.internal.type.ModelType;

@ThreadSafe
public class UnmanagedModelProjection<M> extends TypeCompatibilityModelProjectionSupport<M> {

    public UnmanagedModelProjection(ModelType<M> type) {
        super(type, true, true);
    }

    public UnmanagedModelProjection(ModelType<M> type, boolean canBeViewedAsReadOnly, boolean canBeViewedAsWritable) {
        super(type, canBeViewedAsReadOnly, canBeViewedAsWritable);
    }

    @Override
    protected ModelView<M> toView(MutableModelNode modelNode, ModelRuleDescriptor ruleDescriptor, boolean writable) {
        M instance = modelNode.getPrivateData(getType());
        return InstanceModelView.of(modelNode.getPath(), getType(), instance);
    }

}
