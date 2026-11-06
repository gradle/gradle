/*
 * Copyright 2017 the original author or authors.
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

import org.gradle.api.artifacts.component.ModuleComponentIdentifier;

public final class ModuleComponentFileArtifactIdentifier extends ModuleComponentArtifactIdentifier {
    private final ModuleComponentIdentifier componentId;
    private final String destinationFileName;
    private final String filePath;

    public ModuleComponentFileArtifactIdentifier(ModuleComponentIdentifier componentId, String destinationFileName) {
        this(componentId, destinationFileName, destinationFileName);
    }

    public ModuleComponentFileArtifactIdentifier(ModuleComponentIdentifier componentId, String destinationFileName, String filePath) {
        this.componentId = componentId;
        this.destinationFileName = destinationFileName;
        this.filePath = filePath;
    }

    @Override
    public ModuleComponentIdentifier getComponentIdentifier() {
        return componentId;
    }

    @Override
    public String getDestinationFileName() {
        return destinationFileName;
    }

    @Override
    public String getFilePath() {
        return filePath;
    }
}
