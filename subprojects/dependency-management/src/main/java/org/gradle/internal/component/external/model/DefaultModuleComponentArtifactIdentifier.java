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

package org.gradle.internal.component.external.model;

import org.apache.commons.lang.StringUtils;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.internal.DisplayName;
import org.gradle.internal.component.model.DefaultIvyArtifactName;
import org.gradle.internal.component.model.IvyArtifactName;

import javax.annotation.Nullable;

public class DefaultModuleComponentArtifactIdentifier implements ModuleComponentArtifactIdentifier, DisplayName {
    private final ModuleComponentIdentifier componentIdentifier;
    private final IvyArtifactName name;
    private final int hashCode;

    public DefaultModuleComponentArtifactIdentifier(ModuleComponentIdentifier componentIdentifier, String name, String type, @Nullable String extension) {
        this(componentIdentifier, new DefaultIvyArtifactName(name, type, extension));
    }

    public DefaultModuleComponentArtifactIdentifier(ModuleComponentIdentifier componentIdentifier, String name, String type, @Nullable String extension, @Nullable String classifier) {
        this(componentIdentifier, new DefaultIvyArtifactName(name, type, extension, classifier));
    }

    public DefaultModuleComponentArtifactIdentifier(ModuleComponentIdentifier componentIdentifier, IvyArtifactName artifact) {
        this.componentIdentifier = componentIdentifier;
        this.name = artifact;
        this.hashCode = 31 * name.hashCode() + componentIdentifier.hashCode();
    }

    @Override
    public String getFileName() {
        String classifier = StringUtils.isNotEmpty(name.getClassifier()) ? "-" + name.getClassifier() : "";
        String extension = StringUtils.isNotEmpty(name.getExtension()) ? "." + name.getExtension() : "";
        return name.getName() + "-" + componentIdentifier.getVersion() + classifier + extension;
    }

    @Override
    public String getDisplayName() {
        String name = this.getFileName();
        String componentIdentifier = this.componentIdentifier.toString();
        return name + " (" + componentIdentifier + ")";
    }

    @Override
    public String getCapitalizedDisplayName() {
        return getDisplayName();
    }

    public IvyArtifactName getName() {
        return name;
    }

    @Override
    public ModuleComponentIdentifier getComponentIdentifier() {
        return componentIdentifier;
    }

    @Override
    public String toString() {
        return getDisplayName();
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != getClass()) {
            return false;
        }
        DefaultModuleComponentArtifactIdentifier other = (DefaultModuleComponentArtifactIdentifier) obj;
        return other.componentIdentifier.equals(componentIdentifier)
                && other.name.equals(name);
    }
}
