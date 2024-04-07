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

public class InstrumentationDependencyAnalysis {

    private final InstrumentationArtifactMetadata metadata;
    private final Map<String, Set<String>> dependencies;

    public InstrumentationDependencyAnalysis(
        InstrumentationArtifactMetadata metadata,
        Map<String, Set<String>> dependencies
    ) {
        this.metadata = metadata;
        this.dependencies = dependencies;
    }

    public InstrumentationArtifactMetadata getMetadata() {
        return metadata;
    }

    public Map<String, Set<String>> getDependencies() {
        return dependencies;
    }
}
