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
import org.gradle.api.artifacts.ArtifactIdentifier;
import org.gradle.api.artifacts.ModuleIdentifier;
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

    public String toPath(ArtifactIdentifier artifact) {
        Map<String, Object> attributes = toAttributes(artifact);
        return IvyPatternHelper.substituteTokens(pattern, attributes);
    }

    public String toVersionListPattern(ModuleVersionArtifactMetaData artifactId) {
        Map<String, Object> attributes = toAttributes(artifactId.toArtifactIdentifier());
        attributes.remove(IvyPatternHelper.REVISION_KEY);
        return IvyPatternHelper.substituteTokens(pattern, attributes);
    }

    public String toModulePath(ModuleIdentifier module) {
        throw new UnsupportedOperationException("not implemented yet.");
    }

    public String toModuleVersionPath(ModuleVersionArtifactMetaData artifact) {
        throw new UnsupportedOperationException("not implemented yet.");
    }

    protected Map<String, Object> toAttributes(ArtifactIdentifier artifact) {
        HashMap<String, Object> attributes = new HashMap<String, Object>();
        attributes.put(IvyPatternHelper.ORGANISATION_KEY, artifact.getModuleVersionIdentifier().getGroup());
        attributes.put(IvyPatternHelper.MODULE_KEY, artifact.getModuleVersionIdentifier().getName());
        attributes.put(IvyPatternHelper.REVISION_KEY, artifact.getModuleVersionIdentifier().getVersion());
        attributes.put(IvyPatternHelper.ARTIFACT_KEY, artifact.getName());
        attributes.put(IvyPatternHelper.TYPE_KEY, artifact.getType());
        attributes.put(IvyPatternHelper.EXT_KEY, artifact.getExtension());
        attributes.put("classifier", artifact.getClassifier());
        return attributes;
    }

    protected Map<String, Object> toAttributes(ModuleIdentifier module) {
        HashMap<String, Object> attributes = new HashMap<String, Object>();
        attributes.put(IvyPatternHelper.ORGANISATION_KEY, module.getGroup());
        attributes.put(IvyPatternHelper.MODULE_KEY, module.getName());
        return attributes;
    }
}
