/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.integtests.tooling

import org.gradle.integtests.tooling.fixture.CompositeToolingApiSpecification
import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.tooling.UnsupportedVersionException
import org.gradle.tooling.connection.GradleConnection
import org.gradle.tooling.model.eclipse.EclipseProject

class ToolingApiUnsupportedVersionCompositeBuildCrossVersionSpec extends CompositeToolingApiSpecification {
    @ToolingApiVersion("current")
    @TargetGradleVersion("<1.2")
    def "build execution fails for pre 1.2 providers"() {
        given:
        def singleBuild = multiProjectBuild("single-build", ['a', 'b', 'c'])

        when:
        withCompositeConnection(singleBuild) { GradleConnection connection ->
            connection.newBuild().run()
        }

        then:
        UnsupportedVersionException e = thrown()
        e.message == "Support for builds using Gradle versions older than 1.2 was removed in tooling API version 3.0. You are currently using Gradle version ${targetDist.version.version}. You should upgrade your Gradle build to use Gradle 1.2 or later."
    }

    @ToolingApiVersion("current")
    @TargetGradleVersion("<1.2")
    def "model retrieval fails for pre 1.2 providers"() {
        given:
        def singleBuild = multiProjectBuild("single-build", ['a', 'b', 'c'])

        when:
        withCompositeConnection(singleBuild) { GradleConnection connection ->
            def models = connection.getModels(EclipseProject)
            models*.model
        }

        then:
        UnsupportedVersionException e = thrown()
        e.message == "Support for builds using Gradle versions older than 1.2 was removed in tooling API version 3.0. You are currently using Gradle version ${targetDist.version.version}. You should upgrade your Gradle build to use Gradle 1.2 or later."
    }
}
