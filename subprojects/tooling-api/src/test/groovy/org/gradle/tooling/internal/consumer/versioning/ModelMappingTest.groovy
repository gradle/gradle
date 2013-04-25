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

package org.gradle.tooling.internal.consumer.versioning

import org.gradle.tooling.internal.protocol.*
import org.gradle.tooling.internal.protocol.eclipse.EclipseProjectVersion3
import org.gradle.tooling.internal.protocol.eclipse.HierarchicalEclipseProjectVersion1
import org.gradle.tooling.model.GradleProject
import org.gradle.tooling.model.build.BuildEnvironment
import org.gradle.tooling.model.eclipse.EclipseProject
import org.gradle.tooling.model.eclipse.HierarchicalEclipseProject
import org.gradle.tooling.model.idea.BasicIdeaProject
import org.gradle.tooling.model.idea.IdeaProject
import org.gradle.tooling.model.internal.outcomes.ProjectOutcomes
import spock.lang.Specification

class ModelMappingTest extends Specification {
    final mapping = new ModelMapping()

    def "maps model type to protocol type"() {
        expect:
        mapping.getProtocolType(modelType) == protocolType

        where:
        modelType                  | protocolType
        Void                       | Void
        HierarchicalEclipseProject | HierarchicalEclipseProjectVersion1
        EclipseProject             | EclipseProjectVersion3
        IdeaProject                | InternalIdeaProject
        GradleProject              | InternalGradleProject
        BasicIdeaProject           | InternalBasicIdeaProject
        BuildEnvironment           | InternalBuildEnvironment
        ProjectOutcomes            | InternalProjectOutcomes
    }

    def "can use a protocol type as model type"() {
        expect:
        mapping.getProtocolType(modelType) == modelType

        where:
        modelType << [
                HierarchicalEclipseProjectVersion1,
                EclipseProjectVersion3,
                InternalIdeaProject,
                InternalGradleProject,
                InternalBasicIdeaProject,
                InternalBuildEnvironment,
                InternalProjectOutcomes
        ]
    }

    def "returns null for unknown model type"() {
        expect:
        mapping.getProtocolType(CustomModel) == null
    }
}

interface CustomModel {}
