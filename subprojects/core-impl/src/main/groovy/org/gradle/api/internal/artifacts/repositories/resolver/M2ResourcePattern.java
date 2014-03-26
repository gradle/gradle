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
import org.gradle.api.internal.artifacts.metadata.ModuleVersionArtifactMetaData;

import java.util.Map;

// TODO:DAZ Should extend common base class
public class M2ResourcePattern extends IvyResourcePattern {
    public M2ResourcePattern(String pattern) {
        super(pattern);
    }

    @Override
    public String toString() {
        return String.format("M2 pattern '%s'", getPattern());
    }

    @Override
    public String toModulePath(ModuleIdentifier module) {
        String pattern = getPattern();
        if (!pattern.endsWith(MavenPattern.M2_PATTERN)) {
            throw new UnsupportedOperationException("Cannot locate module for non-maven layout.");
        }
        String metaDataPattern = pattern.substring(0, pattern.length() - MavenPattern.M2_PER_MODULE_PATTERN.length() - 1);
        return IvyPatternHelper.substituteTokens(metaDataPattern, toAttributes(module));
    }

    @Override
    public String toPath(ModuleVersionArtifactMetaData artifact) {
        Map<String, Object> attributes = toAttributes(artifact);
        return IvyPatternHelper.substituteTokens(getPattern(), attributes);
    }

    @Override
    public String toModuleVersionPath(ModuleVersionArtifactMetaData artifact) {
        String pattern = getPattern();
        if (!pattern.endsWith(MavenPattern.M2_PATTERN)) {
            throw new UnsupportedOperationException("Cannot locate module version for non-maven layout.");
        }
        String metaDataPattern = pattern.substring(0, pattern.length() - MavenPattern.M2_PER_MODULE_VERSION_PATTERN.length() - 1);
        return IvyPatternHelper.substituteTokens(metaDataPattern, toAttributes(artifact));
    }

    @Override
    protected Map<String, Object> toAttributes(ModuleVersionArtifactMetaData artifact) {
        return mapOrganisation(super.toAttributes(artifact));
    }

    @Override
    protected Map<String, Object> toAttributes(ModuleIdentifier module) {
        return mapOrganisation(super.toAttributes(module));
    }

    private Map<String, Object> mapOrganisation(Map<String, Object> attributes) {
        String org = (String) attributes.get(IvyPatternHelper.ORGANISATION_KEY);
        if (org != null) {
            attributes.put(IvyPatternHelper.ORGANISATION_KEY, org.replace(".", "/"));
        }
        return attributes;
    }
}
