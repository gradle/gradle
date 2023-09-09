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
import org.gradle.api.internal.artifacts.repositories.PatternHelper;
import org.gradle.internal.component.external.model.ModuleComponentArtifactMetadata;
import org.gradle.internal.component.model.IvyArtifactName;
import org.gradle.internal.resource.ExternalResourceName;

import java.net.URI;
import java.util.Map;

public class M2ResourcePattern extends AbstractResourcePattern {
    public M2ResourcePattern(String pattern) {
        super(pattern);
    }

    public M2ResourcePattern(URI baseUri, String pattern) {
        super(baseUri, pattern);
    }

    @Override
    public String toString() {
        return "M2 pattern '" + getPattern() + "'";
    }

    @Override
    public ExternalResourceName getLocation(ModuleComponentArtifactMetadata artifact) {
        Map<String, String> attributes = toAttributes(artifact);
        String pattern = maybeSubstituteTimestamp(artifact, getBase().getPath());
        return getBase().getRoot().resolve(substituteTokens(pattern, attributes));
    }

    private String maybeSubstituteTimestamp(ModuleComponentArtifactMetadata artifact, String pattern) {
        if (artifact.getComponentId() instanceof MavenUniqueSnapshotComponentIdentifier) {
            MavenUniqueSnapshotComponentIdentifier snapshotId = (MavenUniqueSnapshotComponentIdentifier) artifact.getComponentId();
            pattern = pattern
                    .replaceFirst("-\\[revision]", "-" + snapshotId.getTimestampedVersion())
                    .replace("[revision]", snapshotId.getSnapshotVersion());
        }
        return pattern;
    }

    @Override
    public ExternalResourceName toVersionListPattern(ModuleIdentifier module, IvyArtifactName artifact) {
        Map<String, String> attributes = toAttributes(module, artifact);
        return getBase().getRoot().resolve(substituteTokens(getBase().getPath(), attributes));
    }

    @Override
    public ExternalResourceName toModulePath(ModuleIdentifier module) {
        String pattern = getBase().getPath();
        if (!pattern.endsWith(MavenPattern.M2_PATTERN)) {
            throw new UnsupportedOperationException("Cannot locate module for non-maven layout.");
        }
        String metaDataPattern = pattern.substring(0, pattern.length() - MavenPattern.M2_PER_MODULE_PATTERN.length() - 1);
        return getBase().getRoot().resolve(substituteTokens(metaDataPattern, toAttributes(module)));
    }

    @Override
    public ExternalResourceName toModuleVersionPath(ModuleComponentIdentifier componentIdentifier) {
        String pattern = getBase().getPath();
        if (!pattern.endsWith(MavenPattern.M2_PATTERN)) {
            throw new UnsupportedOperationException("Cannot locate module version for non-maven layout.");
        }
        String metaDataPattern = pattern.substring(0, pattern.length() - MavenPattern.M2_PER_MODULE_VERSION_PATTERN.length() - 1);
        return getBase().getRoot().resolve(substituteTokens(metaDataPattern, toAttributes(componentIdentifier)));
    }

    @Override
    protected String substituteTokens(String pattern, Map<String, String> attributes) {
        String org = attributes.get(PatternHelper.ORGANISATION_KEY);
        if (org != null) {
            attributes.put(PatternHelper.ORGANISATION_KEY, org.replace(".", "/"));
        }
        return super.substituteTokens(pattern, attributes);
    }
}
