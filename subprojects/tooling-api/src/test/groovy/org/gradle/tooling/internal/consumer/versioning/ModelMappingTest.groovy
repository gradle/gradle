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
import org.gradle.tooling.model.gradle.GradleBuild
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

    def "uses null protocol type for custom model type"() {
        expect:
        mapping.getProtocolType(CustomModel) == null
    }

    def "uses null protocol type for model types added after 1.6"() {
        expect:
        mapping.getProtocolType(GradleBuild) == null
    }

    def "maps model type to model identifier"() {
        expect:
        def id = mapping.getModelIdentifierFromModelType(modelType)
        id.name == modelName

        where:
        modelType                  | modelName
        Void                       | ModelIdentifier.NULL_MODEL
        HierarchicalEclipseProject | "org.gradle.tooling.model.eclipse.HierarchicalEclipseProject"
        EclipseProject             | "org.gradle.tooling.model.eclipse.EclipseProject"
        IdeaProject                | "org.gradle.tooling.model.idea.IdeaProject"
        GradleProject              | "org.gradle.tooling.model.GradleProject"
        BasicIdeaProject           | "org.gradle.tooling.model.idea.BasicIdeaProject"
        BuildEnvironment           | "org.gradle.tooling.model.build.BuildEnvironment"
        ProjectOutcomes            | "org.gradle.tooling.model.outcomes.ProjectOutcomes"
        GradleBuild                | "org.gradle.tooling.model.gradle.GradleBuild"
        CustomModel                | CustomModel.name
    }

    def "maps model type to version it was added in"() {
        expect:
        mapping.getVersionAdded(modelType) == since

        where:
        modelType                  | since
        Void                       | "1.0-milestone-3"
        HierarchicalEclipseProject | "1.0-milestone-3"
        EclipseProject             | "1.0-milestone-3"
        IdeaProject                | "1.0-milestone-5"
        GradleProject              | "1.0-milestone-5"
        BasicIdeaProject           | "1.0-milestone-5"
        BuildEnvironment           | "1.0-milestone-8"
        ProjectOutcomes            | "1.2"
        GradleBuild                | "1.8"
        CustomModel                | null
    }
}

interface CustomModel {}
