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

package org.gradle.tooling.composite.internal

import org.gradle.tooling.internal.consumer.DistributionFactory
import spock.lang.Specification

class FirstParticipantDistributionChooserTest extends Specification {
    def projectDir
    def gradleUserHomeDir
    def gradleHome
    def gradleVersion
    def distributionUri
    def distributionFactory

    def setup() {
        projectDir = Mock(File)
        gradleUserHomeDir = Mock(File)
        gradleHome = Mock(File)
        gradleVersion = "2.12"
        distributionUri = new URI("http://distribution_uri")
        distributionFactory = Mock(DistributionFactory)
    }

    def "should use distribution URI if it's set"() {
        given:
        def participant1 = Stub(GradleParticipantBuild)
        def participant2 = Mock(GradleParticipantBuild)
        def participants = [participant1, participant2] as Set
        when:
        FirstParticipantDistributionChooser.chooseDistribution(distributionFactory, participants)
        then:
        participant1.getGradleDistribution() >> distributionUri
        1 * distributionFactory.getDistribution(distributionUri)
        0 * participant2._
    }

    def "should use Gradle home if it's set"() {
        given:
        def participant1 = Mock(GradleParticipantBuild)
        def participant2 = Mock(GradleParticipantBuild)
        def participants = [participant1, participant2] as Set
        when:
        FirstParticipantDistributionChooser.chooseDistribution(distributionFactory, participants)
        then:
        participant1.getGradleDistribution() >> null
        participant1.getGradleHome() >> gradleHome
        1 * distributionFactory.getDistribution(gradleHome)
        0 * participant2._
    }

    def "should use Gradle version if it's set"() {
        given:
        def participant1 = Mock(GradleParticipantBuild)
        def participant2 = Mock(GradleParticipantBuild)
        def participants = [participant1, participant2] as Set
        when:
        FirstParticipantDistributionChooser.chooseDistribution(distributionFactory, participants)
        then:
        participant1.getGradleDistribution() >> null
        participant1.getGradleHome() >> null
        participant1.getGradleVersion() >> gradleVersion
        1 * distributionFactory.getDistribution(gradleVersion)
        0 * participant2._
    }

    def "should use build default Gradle version as last resort"() {
        given:
        def participant1 = Mock(GradleParticipantBuild)
        def participant2 = Mock(GradleParticipantBuild)
        def participants = [participant1, participant2] as Set
        when:
        FirstParticipantDistributionChooser.chooseDistribution(distributionFactory, participants)
        then:
        participant1.getGradleDistribution() >> null
        participant1.getGradleHome() >> null
        participant1.getGradleVersion() >> null
        participant1.getProjectDir() >> projectDir
        1 * distributionFactory.getDefaultDistribution(projectDir, false)
        0 * participant2._
    }

    def "should throw exception where participant is empty"() {
        when:
        FirstParticipantDistributionChooser.chooseDistribution(distributionFactory, [] as Set)
        then:
        thrown(IllegalArgumentException)
    }
}
