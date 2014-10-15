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

package org.gradle.plugins.ide.internal;

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

import org.apache.commons.lang.StringUtils;
import org.gradle.api.artifacts.result.ArtifactResult;
import org.gradle.api.artifacts.result.ResolvedArtifactResult;
import org.gradle.plugins.ide.internal.resolver.model.IdeExtendedRepoFileDependency;

import java.util.HashSet;
import java.util.Set;

public class ShortestNameAuxiliaryArtifactMatchStrategy implements AuxiliaryArtifactMatchStrategy{

    public ResolvedArtifactResult findBestMatch(Set<ArtifactResult> artifacts, IdeExtendedRepoFileDependency dependency) {
        String dependencyFileName = dependency.getFile().getName();

        if (artifacts.size() == 1 && artifacts.iterator().next() instanceof ResolvedArtifactResult) {
            return (ResolvedArtifactResult) artifacts.iterator().next();
        } else if (artifacts.size() > 1) {
            return findMatchingArtifactWithShortestName(pluckResolvedArtifacts(artifacts), dependencyFileName);
        }
        return null;
    }

    private ResolvedArtifactResult findMatchingArtifactWithShortestName(Set<ResolvedArtifactResult> artifacts, String artifactFileName) {
        ResolvedArtifactResult closestMatchSoFar = null;
        for (ResolvedArtifactResult artifactResult : artifacts) {
            String auxiliaryArtifactFileName = artifactResult.getFile().getName();
            if (auxiliaryArtifactFileNameStartsWithArtifactFileName(auxiliaryArtifactFileName, artifactFileName)) {
                if (closestMatchSoFar == null) {
                    closestMatchSoFar = artifactResult;
                } else if (isCloserMatch(auxiliaryArtifactFileName, closestMatchSoFar)) {
                    closestMatchSoFar = artifactResult;
                }
            }
        }
        return closestMatchSoFar;
    }

    private Set<ResolvedArtifactResult> pluckResolvedArtifacts(Set<ArtifactResult> artifacts) {
        Set<ResolvedArtifactResult> resolvedArtifacts = new HashSet<ResolvedArtifactResult>();
        for (ArtifactResult artifact : artifacts) {
            if (artifact instanceof ResolvedArtifactResult) {
                resolvedArtifacts.add((ResolvedArtifactResult) artifact);
            }
        }
        return resolvedArtifacts;
    }

    private boolean isCloserMatch(String resolvedArtifactFileName, ResolvedArtifactResult closestMatchSoFar) {
        return resolvedArtifactFileName.length() < closestMatchSoFar.getFile().getName().length();
    }

    private boolean auxiliaryArtifactFileNameStartsWithArtifactFileName(String auxiliarArtifactFileName, String artifactFileName) {
        String commonEnding = getCommonEnding(auxiliarArtifactFileName, artifactFileName);

        String auxiliaryArtifactName = removeEnding(auxiliarArtifactFileName, commonEnding);
        String artifactName = removeEnding(artifactFileName, commonEnding);

        return auxiliaryArtifactName.startsWith(artifactName);
    }

    private String getCommonEnding(String s1, String s2) {
        StringBuilder commonEnding = new StringBuilder();
        int s1Length = s1.length();
        int s2Length = s2.length();
        for (int i = 1; i <= Math.min(s1Length, s2Length); i++) {
            if (s1.charAt(s1Length - i) == s2.charAt(s2Length - i)) {
                commonEnding.append(s1.charAt(s1Length - i));
            } else {
                break;
            }
        }
        return StringUtils.reverse(commonEnding.toString());
    }

    private String removeEnding(String artifactName, String ending) {
        return artifactName.replace(ending, "");
    }
}
