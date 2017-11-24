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

package org.gradle.plugins.ide.internal.resolver.model;

import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier;

import java.io.File;

public class IdeRepoFileDependency extends IdeDependency {
    private final File file;
    private ModuleComponentIdentifier componentIdentifier;
    private ModuleVersionIdentifier id;

    public IdeRepoFileDependency(File file) {
        this.file = file;
    }

    public File getFile() {
        return file;
    }

    public ModuleVersionIdentifier getId() {
        if (componentIdentifier == null) {
            return null;
        }
        if (id == null) {
            id = new DefaultModuleVersionIdentifier(componentIdentifier.getGroup(), componentIdentifier.getModule(), componentIdentifier.getVersion());
        }
        return id;
    }

    public ModuleComponentIdentifier getComponentIdentifier() {
        return componentIdentifier;
    }

    public void setComponentIdentifier(ModuleComponentIdentifier componentIdentifier) {
        this.componentIdentifier = componentIdentifier;
    }
}
