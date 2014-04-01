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

import org.apache.ivy.core.IvyPatternHelper;
import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.internal.artifacts.metadata.IvyArtifactName;
import org.gradle.api.internal.artifacts.metadata.ModuleVersionArtifactMetaData;

import java.util.HashMap;
import java.util.Map;

public class IvyResourcePattern implements ResourcePattern {
    private final String pattern;

    public IvyResourcePattern(String pattern) {
        this.pattern = pattern;
    }

    public String getPattern() {
        return pattern;
    }

    @Override
    public String toString() {
        return String.format("Ivy pattern '%s'", pattern);
    }

    public String toPath(ModuleVersionArtifactMetaData artifact) {
        Map<String, Object> attributes = toAttributes(artifact);
        return IvyPatternHelper.substituteTokens(pattern, attributes);
    }

    public String toVersionListPattern(ModuleIdentifier module, IvyArtifactName artifact) {
        Map<String, Object> attributes = toAttributes(module);
        attributes.putAll(toAttributes(artifact));
        return IvyPatternHelper.substituteTokens(pattern, attributes);
    }

    public String toModulePath(ModuleIdentifier module) {
        throw new UnsupportedOperationException("not implemented yet.");
    }

    public String toModuleVersionPath(ModuleComponentIdentifier componentIdentifier) {
        throw new UnsupportedOperationException("not implemented yet.");
    }

    protected Map<String, Object> toAttributes(ModuleVersionArtifactMetaData artifact) {
        Map<String, Object> attributes = toAttributes(artifact.getId().getComponentIdentifier());
        attributes.putAll(toAttributes(artifact.getName()));
        return attributes;
    }

    // TODO:DAZ Handle extra attributes
    protected Map<String, Object> toAttributes(IvyArtifactName ivyArtifact) {
        HashMap<String, Object> attributes = new HashMap<String, Object>();
        attributes.put(IvyPatternHelper.ARTIFACT_KEY, ivyArtifact.getName());
        attributes.put(IvyPatternHelper.TYPE_KEY, ivyArtifact.getType());
        attributes.put(IvyPatternHelper.EXT_KEY, ivyArtifact.getExtension());
        attributes.put("classifier", ivyArtifact.getClassifier());
        return attributes;
    }

    protected Map<String, Object> toAttributes(ModuleIdentifier module) {
        HashMap<String, Object> attributes = new HashMap<String, Object>();
        attributes.put(IvyPatternHelper.ORGANISATION_KEY, module.getGroup());
        attributes.put(IvyPatternHelper.MODULE_KEY, module.getName());
        return attributes;
    }

    protected Map<String, Object> toAttributes(ModuleComponentIdentifier componentIdentifier) {
        HashMap<String, Object> attributes = new HashMap<String, Object>();
        attributes.put(IvyPatternHelper.ORGANISATION_KEY, componentIdentifier.getGroup());
        attributes.put(IvyPatternHelper.MODULE_KEY, componentIdentifier.getModule());
        attributes.put(IvyPatternHelper.REVISION_KEY, componentIdentifier.getVersion());
        return attributes;
    }
}
