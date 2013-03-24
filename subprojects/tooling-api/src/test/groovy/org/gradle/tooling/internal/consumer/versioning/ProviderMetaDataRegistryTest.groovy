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

import org.gradle.tooling.internal.protocol.InternalBasicIdeaProject
import org.gradle.tooling.internal.protocol.InternalBuildEnvironment
import org.gradle.tooling.internal.protocol.InternalGradleProject
import org.gradle.tooling.internal.protocol.InternalIdeaProject
import org.gradle.tooling.internal.protocol.InternalProjectOutcomes
import org.gradle.tooling.internal.protocol.ModelIdentifier
import org.gradle.tooling.internal.protocol.eclipse.EclipseProjectVersion3
import org.gradle.tooling.internal.protocol.eclipse.HierarchicalEclipseProjectVersion1
import org.gradle.tooling.model.GradleProject
import org.gradle.tooling.model.Model
import org.gradle.tooling.model.build.BuildEnvironment
import org.gradle.tooling.model.eclipse.EclipseProject
import org.gradle.tooling.model.eclipse.HierarchicalEclipseProject
import org.gradle.tooling.model.idea.BasicIdeaProject
import org.gradle.tooling.model.idea.IdeaProject
import org.gradle.tooling.model.internal.outcomes.ProjectOutcomes
import org.gradle.util.GradleVersion
import spock.lang.Specification

class ProviderMetaDataRegistryTest extends Specification {
    final registry = new ProviderMetaDataRegistry()

    def "determines capabilities for provider version 1.0-m3 and 1.0-m4"() {
        def details = registry.getVersionDetails(version)

        expect:
        details.isModelSupported(HierarchicalEclipseProject)
        details.isModelSupported(EclipseProject)
        details.isModelSupported(Void)

        and:
        !details.isModelSupported(IdeaProject)
        !details.isModelSupported(BasicIdeaProject)
        !details.isModelSupported(GradleProject)
        !details.isModelSupported(BuildEnvironment)
        !details.isModelSupported(ProjectOutcomes)
        !details.isModelSupported(CustomModel)

        and:
        !details.supportsConfiguringJavaHome()
        !details.supportsConfiguringJvmArguments()
        !details.supportsConfiguringStandardInput()
        !details.supportsRunningTasksWhenBuildingModel()
        !details.supportsGradleProjectModel()

        where:
        version << ['1.0-milestone-3', '1.0-milestone-4']
    }

    def "determines capabilities for provider version 1.0-m5 to 1.0-m7"() {
        def details = registry.getVersionDetails(version)

        expect:
        details.isModelSupported(HierarchicalEclipseProject)
        details.isModelSupported(EclipseProject)
        details.isModelSupported(IdeaProject)
        details.isModelSupported(BasicIdeaProject)
        details.isModelSupported(GradleProject)
        details.isModelSupported(Void)

        and:
        !details.isModelSupported(BuildEnvironment)
        !details.isModelSupported(ProjectOutcomes)
        !details.isModelSupported(CustomModel)

        and:
        details.supportsGradleProjectModel()

        and:
        !details.supportsConfiguringJavaHome()
        !details.supportsConfiguringJvmArguments()
        !details.supportsConfiguringStandardInput()
        !details.supportsRunningTasksWhenBuildingModel()

        where:
        version << ['1.0-milestone-5', '1.0-milestone-6', '1.0-milestone-7']
    }

    def "determines whether a model is supported for 1.0-m8 to 1.1"() {
        def details = registry.getVersionDetails(version)

        expect:
        details.isModelSupported(HierarchicalEclipseProject)
        details.isModelSupported(EclipseProject)
        details.isModelSupported(IdeaProject)
        details.isModelSupported(BasicIdeaProject)
        details.isModelSupported(GradleProject)
        details.isModelSupported(BuildEnvironment)
        details.isModelSupported(Void)

        and:
        !details.isModelSupported(ProjectOutcomes)
        !details.isModelSupported(CustomModel)

        and:
        details.supportsGradleProjectModel()
        details.supportsConfiguringJavaHome()
        details.supportsConfiguringJvmArguments()
        details.supportsConfiguringStandardInput()

        and:
        !details.supportsRunningTasksWhenBuildingModel()

        where:
        version << ['1.0-milestone-8', '1.0', '1.1-rc-1', '1.1']
    }

    def "determines whether a model is supported for 1.2-rc-1 to 1.5"() {
        def details = registry.getVersionDetails(version)

        expect:
        details.isModelSupported(HierarchicalEclipseProject)
        details.isModelSupported(EclipseProject)
        details.isModelSupported(IdeaProject)
        details.isModelSupported(BasicIdeaProject)
        details.isModelSupported(GradleProject)
        details.isModelSupported(BuildEnvironment)
        details.isModelSupported(Void)
        details.isModelSupported(ProjectOutcomes)

        and:
        !details.isModelSupported(CustomModel)

        and:
        details.supportsGradleProjectModel()
        details.supportsConfiguringJavaHome()
        details.supportsConfiguringJvmArguments()
        details.supportsConfiguringStandardInput()
        details.supportsRunningTasksWhenBuildingModel()

        where:
        version << ['1.2-rc-1', '1.4-rc-2', '1.5']
    }

    def "determines whether a model is supported for 1.6-rc-1 to current"() {
        def details = registry.getVersionDetails(version)

        expect:
        details.isModelSupported(HierarchicalEclipseProject)
        details.isModelSupported(EclipseProject)
        details.isModelSupported(IdeaProject)
        details.isModelSupported(BasicIdeaProject)
        details.isModelSupported(GradleProject)
        details.isModelSupported(BuildEnvironment)
        details.isModelSupported(ProjectOutcomes)
        details.isModelSupported(Void)
        details.isModelSupported(CustomModel)

        and:
        details.supportsGradleProjectModel()
        details.supportsConfiguringJavaHome()
        details.supportsConfiguringJvmArguments()
        details.supportsConfiguringStandardInput()
        details.supportsRunningTasksWhenBuildingModel()

        where:
        version << ['1.6-rc-1', GradleVersion.current().version]
    }

    def "maps model type to protocol type"() {
        def details = registry.getVersionDetails("1.5")

        expect:
        details.mapModelTypeToProtocolType(modelType) == protocolType

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
        def details = registry.getVersionDetails("1.5")

        expect:
        details.mapModelTypeToProtocolType(modelType) == modelType

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
        def details = registry.getVersionDetails("1.5")

        expect:
        details.mapModelTypeToProtocolType(CustomModel) == null
    }

    def "maps model type to model identifier"() {
        def details = registry.getVersionDetails("1.6-rc-1")

        expect:
        def identifier = details.mapModelTypeToModelIdentifier(modelType)
        identifier.name == modelName
        identifier.version == GradleVersion.current().version

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
        CustomModel                | CustomModel.name
    }
}

interface CustomModel extends Model {}
