/*
 * Copyright 2013 the original author or authors.
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

import com.google.common.collect.Multimap;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;

public interface ModelRegistration {
    ModelRuleDescriptor getDescriptor();

    ModelPath getPath();

    /**
     * Actions that need to be registered when the node is registered.
     */
    Multimap<ModelActionRole, ? extends ModelAction> getActions();

    /**
     * Returns whether the registered node is hidden.
     */
    boolean isHidden();
}
