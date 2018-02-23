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

import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.SelectedArtifactResults;

public class TransientConfigurationResultsLoader {
    private final TransientConfigurationResultsBuilder transientConfigurationResultsBuilder;
    private final ResolvedGraphResults graphResults;

    public TransientConfigurationResultsLoader(TransientConfigurationResultsBuilder transientConfigurationResultsBuilder, ResolvedGraphResults graphResults) {
        this.transientConfigurationResultsBuilder = transientConfigurationResultsBuilder;
        this.graphResults = graphResults;
    }

    /**
     * Creates the result given the selected artifacts.
     */
    public TransientConfigurationResults create(SelectedArtifactResults artifactResults) {
        return transientConfigurationResultsBuilder.load(graphResults, artifactResults);
    }
}
