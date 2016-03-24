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

package org.gradle.tooling.internal.connection
import org.gradle.internal.concurrent.ExecutorFactory
import org.gradle.tooling.internal.consumer.DistributionFactory
import org.gradle.tooling.internal.consumer.LoggingProvider
import org.gradle.tooling.internal.consumer.loader.ToolingImplementationLoader
import spock.lang.Specification

class DefaultGradleConnectionBuilderTest extends Specification {
    final connectionFactory = new GradleConnectionFactory(Mock(ToolingImplementationLoader), Mock(ExecutorFactory), Mock(LoggingProvider))
    final builder = new DefaultGradleConnectionBuilder(connectionFactory, Mock(DistributionFactory))

    final rootDir = new File("build-root")
    final gradleDistribution = new URI("http://www.gradle.org")
    final gradleHome = Mock(File)
    final gradleVersion = "1.0"

    def "requires at least one participant"() {
        when:
        builder.build()
        then:
        thrown(IllegalStateException)
    }

    def "builds a GradleBuild with just a project directory"() {
        when:
        def gradleBuild = builder.newParticipant(rootDir).create()

        then:
        gradleBuild.projectDir.absolutePath == rootDir.absolutePath
        assertBuildDistribution(gradleBuild)
    }

    def "requires a project directory"() {
        when:
        builder.newParticipant(null).create()
        then:
        thrown(IllegalArgumentException)
    }

    def "uses last configured distribution option"() {
        given:
        def participant = builder.newParticipant(rootDir)

        when:
        participant.useDistribution(gradleDistribution)
        participant.useInstallation(gradleHome)
        participant.useGradleVersion(gradleVersion)
        participant.useBuildDistribution()
        def gradleBuild = participant.create()
        then:
        assertBuildDistribution(gradleBuild)

        when:
        participant.useBuildDistribution()
        participant.useDistribution(gradleDistribution)
        participant.useInstallation(gradleHome)
        participant.useGradleVersion(gradleVersion)
        gradleBuild = participant.create()
        then:
        assertGradleVersionDistribution(gradleBuild)

        when:
        participant.useGradleVersion(gradleVersion)
        participant.useBuildDistribution()
        participant.useDistribution(gradleDistribution)
        participant.useInstallation(gradleHome)
        gradleBuild = participant.create()
        then:
        assertFileDistribution(gradleBuild)

        when:
        participant.useInstallation(gradleHome)
        participant.useGradleVersion(gradleVersion)
        participant.useBuildDistribution()
        participant.useDistribution(gradleDistribution)
        gradleBuild = participant.create()
        then:
        assertURIDistribution(gradleBuild)
    }

    void assertBuildDistribution(gradleBuildInternal) {
        assert gradleBuildInternal.gradleDistribution == null
        assert gradleBuildInternal.gradleHome == null
        assert gradleBuildInternal.gradleVersion == null
    }

    void assertURIDistribution(gradleBuildInternal) {
        assert gradleBuildInternal.gradleDistribution == gradleDistribution
        assert gradleBuildInternal.gradleHome == null
        assert gradleBuildInternal.gradleVersion == null
    }

    void assertFileDistribution(gradleBuildInternal) {
        assert gradleBuildInternal.gradleDistribution == null
        assert gradleBuildInternal.gradleHome == gradleHome
        assert gradleBuildInternal.gradleVersion == null
    }

    void assertGradleVersionDistribution(gradleBuildInternal) {
        assert gradleBuildInternal.gradleDistribution == null
        assert gradleBuildInternal.gradleHome == null
        assert gradleBuildInternal.gradleVersion == gradleVersion
    }

}
