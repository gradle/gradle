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
package org.gradle.api.internal.artifacts.ivyservice.resolveengine;

import org.apache.ivy.plugins.latest.ArtifactInfo;
import org.apache.ivy.plugins.latest.LatestRevisionStrategy;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

class LatestModuleConflictResolver implements ModuleConflictResolver {
    public ModuleRevisionResolveState select(Collection<? extends ModuleRevisionResolveState> candidates, ModuleRevisionResolveState root) {
        List<ModuleResolveStateBackedArtifactInfo> artifactInfos = new ArrayList<ModuleResolveStateBackedArtifactInfo>();
        for (ModuleRevisionResolveState moduleRevision : candidates) {
            artifactInfos.add(new ModuleResolveStateBackedArtifactInfo(moduleRevision));
        }
        List<ModuleResolveStateBackedArtifactInfo> sorted = new LatestRevisionStrategy().sort(artifactInfos.toArray(new ArtifactInfo[artifactInfos.size()]));
        return sorted.get(sorted.size() - 1).moduleRevision;
    }

    private static class ModuleResolveStateBackedArtifactInfo implements ArtifactInfo {
        final ModuleRevisionResolveState moduleRevision;

        public ModuleResolveStateBackedArtifactInfo(ModuleRevisionResolveState moduleRevision) {
            this.moduleRevision = moduleRevision;
        }

        public String getRevision() {
            return moduleRevision.getRevision();
        }

        public long getLastModified() {
            throw new UnsupportedOperationException();
        }
    }
}
