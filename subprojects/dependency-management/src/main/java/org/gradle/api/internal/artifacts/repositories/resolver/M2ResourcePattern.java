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

import com.google.common.base.CharMatcher;
import org.apache.ivy.core.IvyPatternHelper;
import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.internal.component.external.model.ModuleComponentArtifactMetadata;
import org.gradle.internal.component.model.IvyArtifactName;
import org.gradle.internal.resource.ExternalResourceName;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

public class M2ResourcePattern extends AbstractResourcePattern {
    /*
     * A matcher whose characters are forbidden to be used by any attribute that is used to substitute a part of the
     * given pattern.
     *
     * There is no clear documentation of the characters that are valid to use for coordinates such as ghroupId,
     * artifactId and others. The official maven guide only states the following for the artifactId:
     * "(...) whatever name you want with lowercase letters and no strange symbols". To be safe, this matcher should
     * only match characters that are surely to be considered "strange".
     *
     * While a whitelist of valid characters would be preferred, this is a blacklist of characters that have proven to
     * be problematic when constructing an URI for retrieval of the artifact.
     */
    private static final CharMatcher INVALID_ATTRIBUTE_CHAR_MATCHER = CharMatcher.anyOf("${}");

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

    public ExternalResourceName getLocation(ModuleComponentArtifactMetadata artifact) {
        Map<String, String> attributes = toAttributes(artifact);
        String pattern = maybeSubstituteTimestamp(artifact, getBase().getPath());
        return getBase().getRoot().resolve(substituteTokens(pattern, attributes));
    }

    private String maybeSubstituteTimestamp(ModuleComponentArtifactMetadata artifact, String pattern) {
        if (artifact.getComponentId() instanceof MavenUniqueSnapshotComponentIdentifier) {
            MavenUniqueSnapshotComponentIdentifier snapshotId = (MavenUniqueSnapshotComponentIdentifier) artifact.getComponentId();
            pattern = pattern
                    .replaceFirst("\\-\\[revision\\]", "-" + snapshotId.getTimestampedVersion())
                    .replace("[revision]", snapshotId.getSnapshotVersion());
        }
        return pattern;
    }

    public ExternalResourceName toVersionListPattern(ModuleIdentifier module, IvyArtifactName artifact) {
        Map<String, String> attributes = toAttributes(module, artifact);
        return getBase().getRoot().resolve(substituteTokens(getBase().getPath(), attributes));
    }

    public ExternalResourceName toModulePath(ModuleIdentifier module) {
        String pattern = getBase().getPath();
        if (!pattern.endsWith(MavenPattern.M2_PATTERN)) {
            throw new UnsupportedOperationException("Cannot locate module for non-maven layout.");
        }
        String metaDataPattern = pattern.substring(0, pattern.length() - MavenPattern.M2_PER_MODULE_PATTERN.length() - 1);
        return getBase().getRoot().resolve(substituteTokens(metaDataPattern, toAttributes(module)));
    }

    public ExternalResourceName toModuleVersionPath(ModuleComponentIdentifier componentIdentifier) {
        String pattern = getBase().getPath();
        if (!pattern.endsWith(MavenPattern.M2_PATTERN)) {
            throw new UnsupportedOperationException("Cannot locate module version for non-maven layout.");
        }
        String metaDataPattern = pattern.substring(0, pattern.length() - MavenPattern.M2_PER_MODULE_VERSION_PATTERN.length() - 1);
        return getBase().getRoot().resolve(substituteTokens(metaDataPattern, toAttributes(componentIdentifier)));
    }

    protected String substituteTokens(String pattern, Map<String, String> attributes) {
        return super.substituteTokens(pattern, toAttributesForSubstitution(attributes));
    }

    private Map<String, String> toAttributesForSubstitution(Map<String, String> attributes) {
        Map<String, String> result = new HashMap<String, String>(attributes);
        for (Map.Entry<String, String> entry : result.entrySet()) {
            String value = entry.getValue();
            if (value != null) {
                // let's just remove the invalid characters until there is a better way to handle them
                entry.setValue(INVALID_ATTRIBUTE_CHAR_MATCHER.removeFrom(value));
            }
        }
        String org = result.get(IvyPatternHelper.ORGANISATION_KEY);
        if (org != null) {
            result.put(IvyPatternHelper.ORGANISATION_KEY, org.replace(".", "/"));
        }
        return result;
    }
}
