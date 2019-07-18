/*
 * Copyright 2019 the original author or authors.
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

import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.internal.component.external.model.IvyModuleComponentIdentifier;

public class IvyModuleVersionIdentifier extends DefaultModuleVersionIdentifier {

    private final String branch;
    private final int hashCode;

    public IvyModuleVersionIdentifier(String group, String name, String version, String branch) {
        super(group, name, version);
        this.branch = branch;
        this.hashCode = 31 * super.hashCode() + branch.hashCode();
    }

    public String getBranch() {
        return branch;
    }

    @Override
    public boolean equals(Object o) {
        return super.equals(o) && ((IvyModuleVersionIdentifier) o).branch.equals(branch);
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    public static ModuleVersionIdentifier newId(String group, String name, String version, String branch) {
        return new IvyModuleVersionIdentifier(group, name, version, branch);
    }

    public static ModuleVersionIdentifier newId(ModuleComponentIdentifier componentId) {
        IvyModuleComponentIdentifier ivyExtendedModuleComponentIdentifier = (IvyModuleComponentIdentifier) componentId;
        return new IvyModuleVersionIdentifier(componentId.getGroup(), componentId.getModule(), componentId.getVersion(), ivyExtendedModuleComponentIdentifier.getBranch());
    }

}
