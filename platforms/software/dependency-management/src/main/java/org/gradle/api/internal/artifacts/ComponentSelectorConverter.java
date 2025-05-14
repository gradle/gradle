/*
 * Copyright 2017 the original author or authors.
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
package org.gradle.api.internal.artifacts;

import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.artifacts.ModuleVersionSelector;
import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;

/**
 * Adapts the new `ComponentSelector` types to legacy types.
 * Note that for `ProjectComponentSelector` this requires looking up the target project in order
 * to determine the project coordinates.
 */
@ServiceScope(Scope.Project.class)
public interface ComponentSelectorConverter {
    ModuleIdentifier getModule(ComponentSelector selector);
    ModuleVersionSelector getSelector(ComponentSelector selector);
}
