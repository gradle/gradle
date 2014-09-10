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

package org.gradle.api.internal.artifacts.component;

import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.internal.artifacts.ModuleInternal;
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier;
import org.gradle.internal.component.local.model.DefaultProjectComponentIdentifier;

public class DefaultComponentIdentifierFactory implements ComponentIdentifierFactory {
    public ComponentIdentifier createComponentIdentifier(ModuleInternal module) {
        String projectPath = module.getProjectPath();

        if(projectPath != null) {
            return new DefaultProjectComponentIdentifier(projectPath);
        }

        return new DefaultModuleComponentIdentifier(module.getGroup(), module.getName(), module.getVersion());
    }
}
