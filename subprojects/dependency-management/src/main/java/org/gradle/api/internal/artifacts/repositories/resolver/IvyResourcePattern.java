/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.internal.artifacts.repositories.resolver;

import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.internal.component.external.model.ModuleComponentArtifactMetadata;
import org.gradle.internal.component.model.IvyArtifactName;
import org.gradle.internal.resource.ExternalResourceName;

import java.net.URI;
import java.util.Map;

public class IvyResourcePattern extends AbstractResourcePattern implements ResourcePattern {

    public IvyResourcePattern(String pattern) {
        super(pattern);
    }

    public IvyResourcePattern(URI baseUri, String pattern) {
        super(baseUri, pattern);
    }

    @Override
    public String toString() {
        return "Ivy pattern '" + getPattern() + "'";
    }

    public ExternalResourceName getLocation(ModuleComponentArtifactMetadata artifact) {
        Map<String, String> attributes = toAttributes(artifact);
        return getBase().getRoot().resolve(substituteTokens(getBase().getPath(), attributes));
    }

    public ExternalResourceName toVersionListPattern(ModuleIdentifier module, IvyArtifactName artifact) {
        Map<String, String> attributes = toAttributes(module, artifact);
        return getBase().getRoot().resolve(substituteTokens(getBase().getPath(), attributes));
    }

    public ExternalResourceName toModulePath(ModuleIdentifier module) {
        throw new UnsupportedOperationException("not implemented yet.");
    }

    public ExternalResourceName toModuleVersionPath(ModuleComponentIdentifier componentIdentifier) {
        throw new UnsupportedOperationException("not implemented yet.");
    }
}
