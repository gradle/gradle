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
package org.gradle.api.internal.artifacts.ivyservice.ivyresolve;

import org.gradle.api.artifacts.ComponentMetadata;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.internal.project.ProjectInternal;

import java.util.List;

public class UserProvidedMetadata implements ComponentMetadata {
    private final ModuleVersionIdentifier id;
    private final List<String> statusScheme;
    private final ImmutableAttributes attributes;

    public UserProvidedMetadata(ModuleVersionIdentifier id, List<String> statusScheme, ImmutableAttributes attributes) {
        this.id = id;
        this.statusScheme = statusScheme;
        this.attributes = attributes;
    }

    @Override
    public ModuleVersionIdentifier getId() {
        return id;
    }

    @Override
    public boolean isChanging() {
        return false;
    }

    @Override
    public String getStatus() {
        return attributes.getAttribute(ProjectInternal.STATUS_ATTRIBUTE);
    }

    @Override
    public List<String> getStatusScheme() {
        return statusScheme;
    }

    @Override
    public ImmutableAttributes getAttributes() {
        return attributes;
    }
}
