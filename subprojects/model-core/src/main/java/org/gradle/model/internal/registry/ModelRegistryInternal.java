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

package org.gradle.model.internal.registry;

import com.google.common.collect.Multimap;
import org.gradle.model.RuleSource;
import org.gradle.model.internal.core.*;
import org.gradle.model.internal.inspect.ExtractedRuleSource;

interface ModelRegistryInternal extends ModelRegistry {
    String getProjectPath();

    ExtractedRuleSource<?> newRuleSource(Class<? extends RuleSource> rules);

    void registerNode(ModelNodeInternal node, Multimap<ModelActionRole, ? extends ModelAction> actions);

    <T> void bind(ModelReference<T> subject, ModelActionRole role, ModelAction mutator);

    void transition(ModelNodeInternal node, ModelNode.State desired, boolean laterOk);
}
