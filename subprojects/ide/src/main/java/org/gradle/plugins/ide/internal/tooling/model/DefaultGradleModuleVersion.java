/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.plugins.ide.internal.tooling.model;

import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.tooling.model.GradleModuleVersion;

import java.io.Serializable;

public class DefaultGradleModuleVersion implements GradleModuleVersion, Serializable {

    private final String group;
    private final String name;
    private final String version;

    public DefaultGradleModuleVersion(ModuleVersionIdentifier identifier) {
        this.group = identifier.getGroup();
        this.name = identifier.getName();
        this.version = identifier.getVersion();
    }

    @Override
    public String getGroup() {
        return group;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getVersion() {
        return version;
    }

    @Override
    public String toString() {
        return "GradleModuleVersion{"
                 + "group='" + group + '\''
                 + ", name='" + name + '\''
                 + ", version='" + version + '\''
                 + '}';
    }
}
