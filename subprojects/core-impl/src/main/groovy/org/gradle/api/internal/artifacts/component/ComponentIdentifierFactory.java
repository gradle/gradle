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
import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.api.artifacts.component.ProjectComponentSelector;

public class ComponentIdentifierFactory {
    private static ComponentIdentifierFactory instance = new ComponentIdentifierFactory();

    public static ComponentIdentifierFactory getInstance() {
        return instance;
    }

    public ComponentIdentifier createIdentifier(ComponentSelector componentSelector) {
        if(componentSelector instanceof ProjectComponentSelector) {
            return DefaultProjectComponentIdentifier.newId(((ProjectComponentSelector) componentSelector).getProjectPath());
        } else if(componentSelector instanceof ModuleComponentSelector) {
            ModuleComponentSelector moduleComponentSelector = (ModuleComponentSelector)componentSelector;
            return DefaultModuleComponentIdentifier.newId(moduleComponentSelector.getGroup(), moduleComponentSelector.getModule(), moduleComponentSelector.getVersion());
        }

        throw new IllegalArgumentException("Unsupported component selector type: " + componentSelector.getClass());
    }
}
