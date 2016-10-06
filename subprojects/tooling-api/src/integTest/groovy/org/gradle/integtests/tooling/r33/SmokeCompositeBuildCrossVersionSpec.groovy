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

package org.gradle.integtests.tooling.r33

import org.gradle.integtests.tooling.fixture.MultiModelToolingApiSpecification
import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiVersions
import org.gradle.tooling.GradleConnectionException
import org.gradle.tooling.UnsupportedVersionException
import org.gradle.tooling.model.eclipse.EclipseProject

/**
 * Basic tests for building and retrieving models from a composite.
 */
@TargetGradleVersion(ToolingApiVersions.SUPPORTS_MULTI_MODEL)
class SmokeCompositeBuildCrossVersionSpec extends MultiModelToolingApiSpecification {

    @TargetGradleVersion("<1.2")
    def "multi model retrieval fails for pre 1.2 providers"() {
        given:
        multiProjectBuildInRootFolder("single-build", ['a', 'b', 'c'])

        when:
        getUnwrappedModels(EclipseProject)

        then:
        UnsupportedVersionException e = thrown()
        e.message == "The version of Gradle you are using ($targetVersion.version) does not support the MultiModelBuilder API. Support for this is available in Gradle 3.1 and all later versions."
    }

    def "throws IllegalArgumentException when trying to retrieve a non-model type"() {
        when:
        getModels(Object)

        then:
        thrown(IllegalArgumentException)
    }

    def "throws IllegalStateException when using a closed connection"() {
        given:
        def singleBuild = singleProjectBuildInSubfolder("project")
        includeBuilds(singleBuild)

        when:
        withConnection { connection ->
            connection.getModels(EclipseProject)
            connection.close()
            connection.getModels(EclipseProject)
        }

        then:
        thrown(IllegalStateException)
    }

    def "propagates errors when trying to retrieve models"() {
        given:
        def singleBuild = singleProjectBuildInSubfolder("project") {
            buildFile << "throw new RuntimeException()"
        }
        includeBuilds(singleBuild)

        when:
        def models = getModels(EclipseProject)

        then:
        models.size() == 1
        def e = models[0].failure
        assertFailure(e, "Could not fetch models of type 'EclipseProject'")
    }

    def "fails to retrieve model when root is not a Gradle project"() {
        setup:
        projectDir.deleteDir()

        when:
        getUnwrappedModels(EclipseProject)

        then:
        def e = thrown(GradleConnectionException)
        assertFailure(e,
            "Could not fetch models of type 'EclipseProject'",
            "Project directory '$projectDir' does not exist.")
    }

    def "does not search upwards for projects"() {
        given:
        projectDir.parentFile.file('settings.gradle') << "include 'project', 'a', 'b', 'c'"

        when:
        def models = getModels(EclipseProject)

        then:
        // should only find 'project', not the other projects defined in root.
        models.size() == 1
        models[0].model.projectDirectory == projectDir
    }
}
