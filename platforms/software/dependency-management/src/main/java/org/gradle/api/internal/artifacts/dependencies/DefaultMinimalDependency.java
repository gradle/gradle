/*
 * Copyright 2020 the original author or authors.
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
package org.gradle.api.internal.artifacts.dependencies;

import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.artifacts.VersionConstraint;

import java.io.Serializable;

public class DefaultMinimalDependency implements MinimalExternalModuleDependencyInternal, Serializable {
    private final ModuleIdentifier module;
    private final VersionConstraint versionConstraint;

    public DefaultMinimalDependency(ModuleIdentifier module, VersionConstraint versionConstraint) {
        this.module = module;
        this.versionConstraint = versionConstraint;
    }

    @Override
    public ModuleIdentifier getModule() {
        return module;
    }

    @Override
    public VersionConstraint getVersionConstraint() {
        return versionConstraint;
    }

    @Override
    public String toString() {
        String versionConstraintAsString = getVersionConstraint().toString();
        return versionConstraintAsString.isEmpty()
            ? getModule().toString()
            : getModule() + ":" + versionConstraintAsString;
    }
}
