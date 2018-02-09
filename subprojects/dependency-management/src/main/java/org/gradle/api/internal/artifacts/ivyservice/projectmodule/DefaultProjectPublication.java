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

package org.gradle.api.internal.artifacts.ivyservice.projectmodule;

import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.internal.component.SoftwareComponentInternal;

import javax.annotation.Nullable;

public class DefaultProjectPublication implements ProjectPublication {
    private final ModuleVersionIdentifier id;

    public DefaultProjectPublication(ModuleVersionIdentifier id) {
        this.id = id;
    }

    @Override
    public ModuleVersionIdentifier getCoordinates() {
        return id;
    }

    @Nullable
    @Override
    public SoftwareComponentInternal getComponent() {
        return null;
    }

    @Override
    public boolean isAlias() {
        return false;
    }
}
