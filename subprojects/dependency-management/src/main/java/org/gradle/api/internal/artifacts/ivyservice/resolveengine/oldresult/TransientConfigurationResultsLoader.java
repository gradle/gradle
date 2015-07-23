/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.resolveengine.oldresult;

import org.gradle.api.artifacts.ModuleDependency;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.internal.artifacts.ResolvedConfigurationIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedArtifactResults;
import org.gradle.internal.Factory;

import java.util.Set;

public class TransientConfigurationResultsLoader implements Factory<TransientConfigurationResults> {
    private final TransientConfigurationResultsBuilder transientConfigurationResultsBuilder;
    private final ResolvedArtifactResults artifactResults;
    private final ResolvedGraphResults graphResults;

    public TransientConfigurationResultsLoader(TransientConfigurationResultsBuilder transientConfigurationResultsBuilder, ResolvedGraphResults graphResults, ResolvedArtifactResults artifactResults) {
        this.transientConfigurationResultsBuilder = transientConfigurationResultsBuilder;
        this.artifactResults = artifactResults;
        this.graphResults = graphResults;
    }

    @Override
    public TransientConfigurationResults create() {
        return transientConfigurationResultsBuilder.load(new ContentMapping());
    }

    private class ContentMapping implements ResolvedContentsMapping {
        @Override
        public Set<ResolvedArtifact> getArtifacts(long id) {
            return artifactResults.getArtifacts(id);
        }

        @Override
        public ModuleDependency getModuleDependency(ResolvedConfigurationIdentifier id) {
            return graphResults.getModuleDependency(id);
        }
    }
}
