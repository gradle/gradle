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

package org.gradle.tooling.internal.composite

import spock.lang.Specification

class DefaultGradleBuildBuilderTest extends Specification {
    def builder = new DefaultGradleBuildBuilder()
    File rootDir = Mock()
    URI gradleDistribution = new URI("http://www.gradle.org")
    File gradleHome = Mock()
    String gradleVersion = "1.0"

    def "builds a GradleBuild with just a project directory"() {
        given:
        def gradleBuild = (GradleBuildInternal)builder.forProjectDirectory(rootDir).create()
        expect:
        gradleBuild.projectDir == rootDir
        assertBuildDistribution()
    }

    def "requires a project directory"() {
        when:
        builder.create()
        then:
        thrown(IllegalArgumentException)
    }

    def "uses last configured distribution option"() {
        given:
        builder.forProjectDirectory(rootDir)

        when:
        builder.useDistribution(gradleDistribution)
        builder.useInstallation(gradleHome)
        builder.useGradleVersion(gradleVersion)
        builder.useBuildDistribution()
        then:
        assertBuildDistribution()

        when:
        builder.useBuildDistribution()
        builder.useDistribution(gradleDistribution)
        builder.useInstallation(gradleHome)
        builder.useGradleVersion(gradleVersion)
        then:
        assertGradleVersionDistribution()

        when:
        builder.useGradleVersion(gradleVersion)
        builder.useBuildDistribution()
        builder.useDistribution(gradleDistribution)
        builder.useInstallation(gradleHome)
        then:
        assertFileDistribution()

        when:
        builder.useInstallation(gradleHome)
        builder.useGradleVersion(gradleVersion)
        builder.useBuildDistribution()
        builder.useDistribution(gradleDistribution)
        then:
        assertURIDistribution()
    }

    void assertBuildDistribution() {
        def gradleBuildInternal = builder.create()
        assert gradleBuildInternal.gradleDistribution == null
        assert gradleBuildInternal.gradleHome == null
        assert gradleBuildInternal.gradleVersion == null
    }

    void assertURIDistribution() {
        def gradleBuildInternal = builder.create()
        assert gradleBuildInternal.gradleDistribution == gradleDistribution
        assert gradleBuildInternal.gradleHome == null
        assert gradleBuildInternal.gradleVersion == null
    }

    void assertFileDistribution() {
        def gradleBuildInternal = builder.create()
        assert gradleBuildInternal.gradleDistribution == null
        assert gradleBuildInternal.gradleHome == gradleHome
        assert gradleBuildInternal.gradleVersion == null
    }

    void assertGradleVersionDistribution() {
        def gradleBuildInternal = builder.create()
        assert gradleBuildInternal.gradleDistribution == null
        assert gradleBuildInternal.gradleHome == null
        assert gradleBuildInternal.gradleVersion == gradleVersion
    }
}
