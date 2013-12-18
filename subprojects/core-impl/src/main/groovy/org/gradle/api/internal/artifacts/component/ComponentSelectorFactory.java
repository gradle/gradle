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

package org.gradle.api.internal.artifacts.component;

import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;

public class ComponentSelectorFactory {
    private static ComponentSelectorFactory instance = new ComponentSelectorFactory();

    public static ComponentSelectorFactory getInstance() {
        return instance;
    }

    public ComponentSelector createSelector(ComponentIdentifier componentIdentifier) {
        if(componentIdentifier instanceof ProjectComponentIdentifier) {
            return DefaultProjectComponentSelector.newSelector(((ProjectComponentIdentifier) componentIdentifier).getProjectPath());
        } else if(componentIdentifier instanceof ModuleComponentIdentifier) {
            ModuleComponentIdentifier moduleComponentIdentifier = (ModuleComponentIdentifier)componentIdentifier;
            return DefaultModuleComponentSelector.newSelector(moduleComponentIdentifier.getGroup(), moduleComponentIdentifier.getModule(), moduleComponentIdentifier.getVersion());
        }

        throw new IllegalArgumentException("Unsupported component idenfifier type: " + componentIdentifier.getClass());
    }
}
