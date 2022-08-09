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

import org.gradle.api.artifacts.MinimalExternalModuleDependency;
import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.artifacts.MutableVersionConstraint;

import java.io.Serializable;

public class DefaultMinimalDependency implements MinimalExternalModuleDependency, Serializable {
    private final ModuleIdentifier module;
    private final MutableVersionConstraint versionConstraint;
    private final int hashCode;

    public DefaultMinimalDependency(ModuleIdentifier module, MutableVersionConstraint versionConstraint) {
        this.module = module;
        this.versionConstraint = versionConstraint;
        this.hashCode = doComputeHashCode();
    }

    @Override
    public ModuleIdentifier getModule() {
        return module;
    }

    @Override
    public MutableVersionConstraint getVersionConstraint() {
        return versionConstraint;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        DefaultMinimalDependency that = (DefaultMinimalDependency) o;

        if (!module.equals(that.module)) {
            return false;
        }
        return versionConstraint.equals(that.versionConstraint);
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    private int doComputeHashCode() {
        int result = module.hashCode();
        result = 31 * result + versionConstraint.hashCode();
        return result;
    }

    @Override
    public String toString() {
        String versionConstraintAsString = versionConstraint.toString();
        return versionConstraintAsString.isEmpty()
            ? module.toString()
            : module + ":" + versionConstraintAsString;
    }
}
