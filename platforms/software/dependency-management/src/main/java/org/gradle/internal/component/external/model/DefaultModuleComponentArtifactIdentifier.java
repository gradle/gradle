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

import org.apache.commons.lang3.StringUtils;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.internal.component.model.DefaultIvyArtifactName;
import org.gradle.internal.component.model.IvyArtifactName;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

public final class DefaultModuleComponentArtifactIdentifier extends ModuleComponentArtifactIdentifier {
    private final ModuleComponentIdentifier componentIdentifier;
    private final IvyArtifactName name;

    public DefaultModuleComponentArtifactIdentifier(ModuleComponentIdentifier componentIdentifier, String name, String type, @Nullable String extension) {
        this(componentIdentifier, new DefaultIvyArtifactName(name, type, extension));
    }

    public DefaultModuleComponentArtifactIdentifier(ModuleComponentIdentifier componentIdentifier, String name, String type, @Nullable String extension, @Nullable String classifier) {
        this(componentIdentifier, new DefaultIvyArtifactName(name, type, extension, classifier));
    }

    public DefaultModuleComponentArtifactIdentifier(ModuleComponentIdentifier componentIdentifier, IvyArtifactName artifact) {
        this.componentIdentifier = componentIdentifier;
        this.name = artifact;
    }

    @Override
    public String getDestinationFileName() {
        String classifier = StringUtils.isNotEmpty(name.getClassifier()) ? "-" + name.getClassifier() : "";
        String extension = StringUtils.isNotEmpty(name.getExtension()) ? "." + name.getExtension() : "";
        return name.getName() + "-" + componentIdentifier.getVersion() + classifier + extension;
    }

    // This is only a guess at the file path, as the actual path may depend on the repository layout and other factors.
    // It's used here to provide a default value for the file path, but it may not be accurate in all cases.
    @Override
    public String getFilePath() {
        String fileName = getDestinationFileName();
        if (!Objects.equals(name.getType(), name.getExtension())) {
            // Assume that types are stored in a directory named after the type, e.g., "jars/", "zips/", etc.
            // This is to provide a more accurate guess at the Ivy file path based on the default Ivy layout.
            return name.getType() + "s/" + fileName;
        }
        return fileName;
    }

    public IvyArtifactName getName() {
        return name;
    }

    @Override
    public ModuleComponentIdentifier getComponentIdentifier() {
        return componentIdentifier;
    }
}
