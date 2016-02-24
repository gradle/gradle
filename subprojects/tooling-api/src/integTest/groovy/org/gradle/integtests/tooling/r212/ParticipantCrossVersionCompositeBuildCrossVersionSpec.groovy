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

package org.gradle.integtests.tooling.r212
import org.gradle.api.specs.Spec
import org.gradle.integtests.fixtures.executer.GradleDistribution
import org.gradle.integtests.fixtures.versions.ReleasedVersionDistributions
import org.gradle.integtests.tooling.fixture.CompositeToolingApiSpecification
import org.gradle.tooling.model.eclipse.EclipseProject
import org.gradle.util.CollectionUtils
import org.gradle.util.GradleVersion
import spock.lang.Ignore

/**
 * Tests composites with a different Gradle version than the client and coordinator.
 */
@Ignore // TODO: Broken on Java 6?
class ParticipantCrossVersionCompositeBuildCrossVersionSpec extends CompositeToolingApiSpecification {

    private final static List GRADLE_ALL_RELEASES = new ReleasedVersionDistributions().all
    private final static List SUPPORTED_GRADLE_VERSIONS = CollectionUtils.filter(GRADLE_ALL_RELEASES, new UnsupportedGradleVersionSpec())

    private final static class UnsupportedGradleVersionSpec implements Spec<GradleDistribution> {
        private final static GradleVersion MINIMUM_SUPPORTED_VERSION = GradleVersion.version("1.0")

        @Override
        boolean isSatisfiedBy(GradleDistribution distribution) {
            if (distribution.version.compareTo(MINIMUM_SUPPORTED_VERSION) < 0) {
                // too old
                return false
            }

            return true
        }
    }

    def "retrieve EclipseProject from all supported Gradle versions #gradleDist"() {
        given:
        def project = populate("project") {
            buildFile << "apply plugin: 'java'"
        }

        when:
        def builder = createCompositeBuilder()
        builder.addBuild(project.absoluteFile, gradleDist.gradleHomeDir)
        def connection = builder.build()
        def models = connection.getModels(EclipseProject)
        then:
        models.size() == 1
        cleanup:
        connection?.close()

        where:
        gradleDist << SUPPORTED_GRADLE_VERSIONS
    }
}
