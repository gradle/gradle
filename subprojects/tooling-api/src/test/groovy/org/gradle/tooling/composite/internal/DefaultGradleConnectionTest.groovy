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

import org.gradle.tooling.composite.GradleConnection
import org.gradle.tooling.model.eclipse.EclipseProject
import spock.lang.Specification
import spock.lang.Unroll

class DefaultGradleConnectionTest extends Specification {

    GradleParticipantBuild build = Mock()
    GradleConnection connection = new DefaultGradleConnection(null, [ build ] as Set)

    def "uses gradle user home specified by builder"() {
        def builder = new DefaultGradleConnection.Builder()
        File projectDir = Mock()
        File gradleUserHome = Mock()
        builder.useGradleUserHomeDir(gradleUserHome)
        builder.addBuild(projectDir)
        DefaultGradleConnection connection = builder.build()

        expect:
        connection.participants[0].gradleUserHomeDir == gradleUserHome
    }

    @Unroll
    def "participant project uses specified gradle distribution from #distributionType"() {
        def builder = new DefaultGradleConnection.Builder()
        File projectDir = Mock()
        builder.addBuild(projectDir, value)
        DefaultGradleConnection connection = builder.build()

        expect:
        connection.participants[0].projectDir == projectDir
        connection.participants[0]."${distributionType}" == value

        where:
        distributionType     | value
        "gradleHome"         | Mock(File)
        "gradleVersion"      | "2.0"
        "gradleDistribution" | new URI("http://example.com")
    }

    def "can get model builder"() {
        expect:
        connection.models(EclipseProject) instanceof DefaultCompositeModelBuilder
    }

    def modelTypeMustBeAnInterface() {
        when:
        connection.models(String)

        then:
        IllegalArgumentException e = thrown()
        e.message == "Cannot fetch a model of type 'java.lang.String' as this type is not an interface."
    }


    def "close stops all underlying project connections"() {
        given:
        def builds = (0..3).collect { Mock(GradleParticipantBuild) } as Set
        GradleConnection connection = new DefaultGradleConnection(null, builds)
        when:
        connection.close()
        then:
        builds.each {
            1 * it.stop()
        }
    }

    def "errors propagate to caller when closing connection"() {
        given:
        build.stop() >> { throw new RuntimeException() }
        when:
        connection.close()
        then:
        thrown(RuntimeException)
    }
}
