/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.api.internal.initialization.transform;

import java.util.Map;
import java.util.Set;

public class InstrumentationArtifactAnalysis {

    private final InstrumentationArtifactMetadata metadata;
    private final Map<String, Set<String>> typeHierarchy;
    private final Set<String> dependencies;

    public InstrumentationArtifactAnalysis(
        InstrumentationArtifactMetadata metadata,
        Map<String, Set<String>> typeHierarchy,
        Set<String> dependencies
    ) {
        this.metadata = metadata;
        this.typeHierarchy = typeHierarchy;
        this.dependencies = dependencies;
    }

    public InstrumentationArtifactMetadata getMetadata() {
        return metadata;
    }

    public Map<String, Set<String>> getTypeHierarchy() {
        return typeHierarchy;
    }

    public Set<String> getDependencies() {
        return dependencies;
    }
}
