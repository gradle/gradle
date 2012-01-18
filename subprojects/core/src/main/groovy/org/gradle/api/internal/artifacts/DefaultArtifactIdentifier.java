/*
 * Copyright 2011 the original author or authors.
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

import org.gradle.api.artifacts.ArtifactIdentifier;
import org.gradle.api.artifacts.ModuleVersionIdentifier;

public class DefaultArtifactIdentifier implements ArtifactIdentifier {
    private final ModuleVersionIdentifier moduleVersionIdentifier;
    private final String name;
    private final String type;
    private final String extension;
    private final String classifier;

    public DefaultArtifactIdentifier(ModuleVersionIdentifier moduleVersionIdentifier, String name, String type, String extension, String classifier) {
        this.moduleVersionIdentifier = moduleVersionIdentifier;
        this.name = name;
        this.type = type;
        this.extension = extension;
        this.classifier = classifier;
    }

    public ModuleVersionIdentifier getModuleVersionIdentifier() {
        return moduleVersionIdentifier;
    }

    public String getName() {
        return name;
    }

    public String getType() {
        return type;
    }

    public String getExtension() {
        return extension;
    }

    public String getClassifier() {
        return classifier;
    }
}
