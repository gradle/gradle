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
package org.gradle.api.internal.artifacts.result;

import org.gradle.api.artifacts.component.ComponentArtifactIdentifier;
import org.gradle.api.artifacts.result.ResolvedArtifactResult;
import org.gradle.api.artifacts.result.ResolvedVariantResult;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.component.Artifact;

import java.io.File;

public class DefaultResolvedArtifactResult implements ResolvedArtifactResult {
    private final ComponentArtifactIdentifier identifier;
    private final ResolvedVariantResult variant;
    private final Class<? extends Artifact> type;
    private final File file;

    public DefaultResolvedArtifactResult(ComponentArtifactIdentifier identifier, AttributeContainer variantAttributes, Class<? extends Artifact> type, File file) {
        this.identifier = identifier;
        this.variant = new DefaultResolvedVariantResult(variantAttributes);
        this.type = type;
        this.file = file;
    }

    @Override
    public String toString() {
        return identifier.getDisplayName();
    }

    @Override
    public ComponentArtifactIdentifier getId() {
        return identifier;
    }

    public Class<? extends Artifact> getType() {
        return type;
    }

    public File getFile() {
        return file;
    }

    @Override
    public ResolvedVariantResult getVariant() {
        return variant;
    }
}
