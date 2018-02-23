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

package org.gradle.model.internal.inspect;

import org.gradle.model.internal.core.ModelAction;
import org.gradle.model.internal.core.ModelPath;
import org.gradle.model.internal.registry.ModelRegistry;

public interface MethodModelRuleApplicationContext {
    ModelRegistry getRegistry();

    /**
     * Contextualizes the given action.
     *
     * - Resolved subject and input paths relative to the target element.
     * - Resolves subject type references relative to the target element.
     * - Adds implicit inputs.
     *
     * The returned action invokes the provided action with a {@link ModelRuleInvoker} contextualized with implicit inputs.
     */
    ModelAction contextualize(MethodRuleAction action);

    ModelPath getScope();
}
