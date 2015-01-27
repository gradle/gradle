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

import org.gradle.api.Action;
import org.gradle.internal.BiAction;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;
import org.gradle.model.internal.manage.schema.ModelSchema;

import java.util.List;

public interface ModelCreatorFactory {
    <T> ModelCreator creator(ModelRuleDescriptor descriptor,
                             ModelPath path,
                             ModelSchema<T> schema,
                             Action<? super T> initializer);

    <T> ModelCreator creator(ModelRuleDescriptor descriptor,
                             ModelPath path,
                             ModelSchema<T> schema,
                             List<ModelReference<?>> initializerInputs,
                             BiAction<? super T, ? super List<ModelView<?>>> initializer);

    <T> ModelCreator creator(ModelRuleDescriptor descriptor,
                             ModelPath path,
                             ModelSchema<T> schema);
}
