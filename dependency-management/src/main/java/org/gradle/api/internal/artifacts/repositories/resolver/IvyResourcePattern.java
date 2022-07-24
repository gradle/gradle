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

import com.google.common.collect.ImmutableMap;
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

    @Override
    public ExternalResourceName getLocation(ModuleComponentArtifactMetadata artifact) {
        Map<String, String> attributes = toAttributes(artifact);
        return getBase().getRoot().resolve(substituteTokens(getBase().getPath(), attributes));
    }

    @Override
    public ExternalResourceName toVersionListPattern(ModuleIdentifier module, IvyArtifactName artifact) {
        Map<String, String> attributes = toAttributes(module, artifact);
        return getBase().getRoot().resolve(substituteTokens(getBase().getPath(), attributes));
    }

    @Override
    public ExternalResourceName toModulePath(ModuleIdentifier module) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ExternalResourceName toModuleVersionPath(ModuleComponentIdentifier componentIdentifier) {
        ImmutableMap<String, String> attributes = ImmutableMap.of(
            "organisation", componentIdentifier.getGroup(),
            "module", componentIdentifier.getModule(),
            "artifact", componentIdentifier.getModule(),
            "revision", componentIdentifier.getVersion()
        );
        ExternalResourceName resolve = getBase().getRoot().resolve(substituteTokens(getPathWithoutArtifactPart(), attributes));
        return resolve;
    }

    protected String getPathWithoutArtifactPart() {
        String path = getBase().getPath();
        int i = path.lastIndexOf('/');
        if (i>0) {
            i = path.indexOf("/[artifact]", i);
        }
        if (i<0) {
            throw new UnsupportedOperationException("Cannot locate module version for non standard Ivy layout.");
        }
        return path.substring(0, i);
    }
}
