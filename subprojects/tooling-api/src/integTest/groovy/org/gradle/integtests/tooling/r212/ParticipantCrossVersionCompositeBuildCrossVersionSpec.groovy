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
import org.junit.Assume
import spock.lang.Ignore

/**
 * Tests composites with a different Gradle version than the client and coordinator.
 */
class ParticipantCrossVersionCompositeBuildCrossVersionSpec extends CompositeToolingApiSpecification {

    private final static List GRADLE_ALL_RELEASES = new ReleasedVersionDistributions().all
    private final static List SUPPORTED_GRADLE_VERSIONS = CollectionUtils.filter(GRADLE_ALL_RELEASES, new UnsupportedGradleVersionSpec())
    private final static List QUIRKY_GRADLE_VERSIONS = CollectionUtils.filter(GRADLE_ALL_RELEASES, new QuirkyGradleVersionSpec())

    private final static class UnsupportedGradleVersionSpec implements Spec<GradleDistribution> {
        @Override
        boolean isSatisfiedBy(GradleDistribution distribution) {
            def current = distribution.version
            def minimumGradleVersion = current.getClass().version("1.0")
            if (distribution.version.compareTo(minimumGradleVersion) < 0) {
                // too old
                return false
            }

            return true
        }
    }

    // These versions of Gradle don't seem to work with the coordinator as participants?
    private final static class QuirkyGradleVersionSpec implements Spec<GradleDistribution> {
        @Override
        boolean isSatisfiedBy(GradleDistribution distribution) {
            def current = distribution.version
            def minimumGradleVersion = current.getClass().version("1.8")
            def maximumGradleVersion = current.getClass().version("2.1")
            if (current.compareTo(minimumGradleVersion) < 0 || current.compareTo(maximumGradleVersion) > 0) {
                // not quirky
                return false
            }

            return true
        }
    }

    @Ignore("Spawns too many daemons and loads all versions of Gradle into the test process")
    def "retrieve EclipseProject from all supported Gradle versions #gradleDist"() {
        given:
        GradleDistribution distribution = gradleDist
        def project = populate("project") {
            buildFile << "apply plugin: 'java'"
        }

        when:
        println "Testing with ${distribution.version}"
        Assume.assumeFalse("${distribution.version} doesn't seem to work?", distribution in QUIRKY_GRADLE_VERSIONS)
        def builder = createCompositeBuilder()
        builder.addBuild(project.absoluteFile, distribution.gradleHomeDir)
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
