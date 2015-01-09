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
import org.gradle.model.internal.core.Inputs;
import org.gradle.model.internal.core.ModelAction;
import org.gradle.model.internal.core.ModelActionRole;
import org.gradle.model.internal.core.MutableModelNode;
import org.gradle.model.internal.manage.instance.ModelInstantiator;
import org.gradle.model.internal.manage.schema.ModelSchema;

class ManagedSetInitializer<T> implements BiAction<MutableModelNode, Inputs> {
    private final ModelInstantiator modelInstantiator;
    private final ModelSchema<T> schema;
    private final ModelAction<T> modelAction;

    public ManagedSetInitializer(ModelInstantiator modelInstantiator, ModelSchema<T> schema, ModelAction<T> modelAction) {
        this.modelInstantiator = modelInstantiator;
        this.schema = schema;
        this.modelAction = modelAction;
    }

    @Override
    public void execute(MutableModelNode modelNode, Inputs inputs) {
        T instance = modelInstantiator.newInstance(schema);
        modelNode.setPrivateData(schema.getType(), instance);
        modelNode.applyToSelf(ModelActionRole.Initialize, modelAction);
    }
}
