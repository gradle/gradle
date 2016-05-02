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

    def "builds a participant with just a project directory"() {
        when:
        builder.addParticipant(rootDir)
        def gradleBuild = builder.participantBuilders.first().build()

        then:
        gradleBuild.projectDir.absolutePath == rootDir.absolutePath
        assertBuildDistribution(gradleBuild)
    }

    def "participant requires a project directory"() {
        when:
        builder.addParticipant(null)
        then:
        thrown(IllegalArgumentException)
    }

    def "uses last configured distribution option for participant"() {
        when:
        def participant1 = builder.addParticipant(rootDir)
        participant1.useDistribution(gradleDistribution)
        participant1.useInstallation(gradleHome)
        participant1.useGradleVersion(gradleVersion)
        participant1.useBuildDistribution()
        then:
        assertBuildDistribution(builder.participantBuilders.last().build())

        when:
        def participant2 = builder.addParticipant(rootDir)
        participant2.useBuildDistribution()
        participant2.useDistribution(gradleDistribution)
        participant2.useInstallation(gradleHome)
        participant2.useGradleVersion(gradleVersion)
        then:
        assertGradleVersionDistribution(builder.participantBuilders.last().build())

        when:
        def participant3 = builder.addParticipant(rootDir)
        participant3.useGradleVersion(gradleVersion)
        participant3.useBuildDistribution()
        participant3.useDistribution(gradleDistribution)
        participant3.useInstallation(gradleHome)
        then:
        assertFileDistribution(builder.participantBuilders.last().build())

        when:
        def participant4 = builder.addParticipant(rootDir)
        participant4.useInstallation(gradleHome)
        participant4.useGradleVersion(gradleVersion)
        participant4.useBuildDistribution()
        participant4.useDistribution(gradleDistribution)
        then:
        assertURIDistribution(builder.participantBuilders.last().build())
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
