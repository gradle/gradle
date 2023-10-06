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

import com.google.common.collect.Multimap;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;

/**
 * A standalone strategy for initializing a node.
 * <p>
 * Differs from {@link ModelRegistration} in that it's more of a template for a creator.
 * It does not say anything about the actual entity (e.g. its path) or the identity of the creation rule.
 *
 * @see ModelRegistrations
 */
public interface NodeInitializer {

    Multimap<ModelActionRole, ModelAction> getActions(ModelReference<?> subject, ModelRuleDescriptor descriptor);

}
