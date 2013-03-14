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
import org.gradle.tooling.internal.protocol.eclipse.EclipseProjectVersion3
import org.gradle.tooling.internal.protocol.eclipse.HierarchicalEclipseProjectVersion1
import org.gradle.tooling.model.Model
import org.gradle.util.GradleVersion
import spock.lang.Specification

class ProviderMetaDataRegistryTest extends Specification {
    final registry = new ProviderMetaDataRegistry()

    def "determines whether a model is supported for 1.0-m3 and 1.0-m4"() {
        def details = registry.getVersionDetails(version)

        expect:
        details.isModelSupported(HierarchicalEclipseProjectVersion1)
        details.isModelSupported(EclipseProjectVersion3)

        and:
        !details.isModelSupported(InternalIdeaProject)
        !details.isModelSupported(InternalBasicIdeaProject)
        !details.isModelSupported(InternalGradleProject)
        !details.isModelSupported(InternalBuildEnvironment)
        !details.isModelSupported(InternalProjectOutcomes)
        !details.isModelSupported(Void)
        !details.isModelSupported(CustomModel)

        where:
        version << ['1.0-milestone-3', '1.0-milestone-4']
    }

    def "determines whether a model is supported for 1.0-m5 to 1.0-m7"() {
        def details = registry.getVersionDetails(version)

        expect:
        details.isModelSupported(HierarchicalEclipseProjectVersion1)
        details.isModelSupported(EclipseProjectVersion3)
        details.isModelSupported(InternalIdeaProject)
        details.isModelSupported(InternalBasicIdeaProject)
        details.isModelSupported(InternalGradleProject)

        and:
        !details.isModelSupported(InternalBuildEnvironment)
        !details.isModelSupported(InternalProjectOutcomes)
        !details.isModelSupported(Void)
        !details.isModelSupported(CustomModel)

        where:
        version << ['1.0-milestone-5', '1.0-milestone-6', '1.0-milestone-7']
    }

    def "determines whether a model is supported for 1.0-m8 to 1.1"() {
        def details = registry.getVersionDetails(version)

        expect:
        details.isModelSupported(HierarchicalEclipseProjectVersion1)
        details.isModelSupported(EclipseProjectVersion3)
        details.isModelSupported(InternalIdeaProject)
        details.isModelSupported(InternalBasicIdeaProject)
        details.isModelSupported(InternalGradleProject)
        details.isModelSupported(InternalBuildEnvironment)

        and:
        !details.isModelSupported(InternalProjectOutcomes)
        !details.isModelSupported(Void)
        !details.isModelSupported(CustomModel)

        where:
        version << ['1.0-milestone-8', '1.0', '1.1-rc-1', '1.1']
    }

    def "determines whether a model is supported for 1.2-rc-1 to 1.5"() {
        def details = registry.getVersionDetails(version)

        expect:
        details.isModelSupported(HierarchicalEclipseProjectVersion1)
        details.isModelSupported(EclipseProjectVersion3)
        details.isModelSupported(InternalIdeaProject)
        details.isModelSupported(InternalBasicIdeaProject)
        details.isModelSupported(InternalGradleProject)
        details.isModelSupported(InternalBuildEnvironment)
        details.isModelSupported(Void)
        details.isModelSupported(InternalProjectOutcomes)

        and:
        !details.isModelSupported(CustomModel)

        where:
        version << ['1.2-rc-1', '1.4-rc-2', '1.5']
    }

    def "determines whether a model is supported for 1.6-rc-1 to current"() {
        def details = registry.getVersionDetails(version)

        expect:
        details.isModelSupported(HierarchicalEclipseProjectVersion1)
        details.isModelSupported(EclipseProjectVersion3)
        details.isModelSupported(InternalIdeaProject)
        details.isModelSupported(InternalBasicIdeaProject)
        details.isModelSupported(InternalGradleProject)
        details.isModelSupported(InternalBuildEnvironment)
        details.isModelSupported(InternalProjectOutcomes)
        details.isModelSupported(Void)
        details.isModelSupported(CustomModel)

        where:
        version << ['1.6-rc-1', GradleVersion.current().version]
    }
}

interface CustomModel extends Model {}
