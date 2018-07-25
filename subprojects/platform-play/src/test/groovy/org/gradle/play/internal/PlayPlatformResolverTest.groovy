/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.play.internal

import org.gradle.api.InvalidUserDataException
import org.gradle.api.JavaVersion
import org.gradle.platform.base.internal.DefaultPlatformRequirement
import org.gradle.play.internal.platform.PlayMajorVersion
import org.gradle.play.platform.PlayPlatform
import spock.lang.Specification
import spock.lang.Unroll

import static org.gradle.play.internal.PlayPlatformResolver.*

class PlayPlatformResolverTest extends Specification {
    def resolver = new PlayPlatformResolver()

    def "provides correct platform type"() {
        expect:
        resolver.getType() == PlayPlatform.class
    }

    def "fails to resolve invalid play platform"() {
        when:
        resolve "java-1.6.0"

        then:
        def e = thrown(InvalidUserDataException)
        e.message == "Not a valid Play platform: java-1.6.0."
    }

    def "fails to resolve unsupported play version"() {
        when:
        resolve requirement

        then:
        def e = thrown(InvalidUserDataException)
        e.message == "Not a supported Play version: 2.1.0. This plugin is compatible with: [${PlayMajorVersion.values().join(', ')}]."

        where:
        requirement << ["play-2.1.0", [play: '2.1.0']]
    }

    def "resolves platform for play"() {
        when:
        def playPlatform = resolve("play-${playVersion}")

        then:
        assertPlayPlatform(playPlatform, play: playVersion, scala: scalaVersion)

        when:
        playPlatform = resolve play: playVersion

        then:
        assertPlayPlatform(playPlatform, play: playVersion, scala: scalaVersion)

        where:
        playVersion | scalaVersion
        "2.2.3"     | "2.10.7"
        "2.3.4"     | "2.11.12"
        "2.4.8"     | "2.11.12"
        "2.5.4"     | "2.11.12"
        "2.6.5"     | "2.12.6"
    }

    private void assertPlayPlatform(Map versions, PlayPlatform platform) {
        assert platform.name == "play-${versions.play}".toString()
        assert platform.playVersion == versions.play
        assert platform.javaPlatform.targetCompatibility == JavaVersion.current()
        assert platform.scalaPlatform.scalaVersion == versions.scala
    }

    def "resolves platform with specified scala version"() {
        when:
        def playPlatform = resolve play: "2.3.1", scala: "2.10"

        then:
        playPlatform.name == "play-2.3.1-2.10"
        playPlatform.playVersion == "2.3.1"
        playPlatform.javaPlatform.targetCompatibility == JavaVersion.current()
        playPlatform.scalaPlatform.scalaVersion == "2.10.7"
    }

    @Unroll
    def "fails to resolve Play platform #playVersion incompatible Scala version #scalaVersion"() {
        when:
        resolve play: playVersion, scala: scalaVersion

        then:
        def e = thrown(InvalidUserDataException)
        e.message == message

        where:
        playVersion | scalaVersion | message
        '2.6.5'     | '2.10'       | "Play versions 2.6.x are not compatible with Scala platform 2.10. Compatible Scala platforms are [2.12, 2.11]."
        '2.5.4'     | '2.10'       | "Play versions 2.5.x are not compatible with Scala platform 2.10. Compatible Scala platforms are [2.11]."
        '2.2.6'     | '2.11'       | "Play versions 2.2.x are not compatible with Scala platform 2.11. Compatible Scala platforms are [2.10]."
        '2.2.6'     | '2.9'        | "Not a supported Scala platform identifier 2.9. Supported values are: [${LATEST_SCALA_VERSIONS.keySet().join(', ')}]."
        '2.2.6'     | '2.10.6'     | "Not a supported Scala platform identifier 2.10.6. Supported values are: [${LATEST_SCALA_VERSIONS.keySet().join(', ')}]."
    }

    def "resolves platform with specified java version"() {
        when:
        def playPlatform = resolve play: "2.3.1", java: "1.6"

        then:
        playPlatform.name == "play-2.3.1_1.6"
        playPlatform.playVersion == "2.3.1"
        playPlatform.javaPlatform.targetCompatibility == JavaVersion.toVersion("1.6")
        playPlatform.scalaPlatform.scalaVersion == "2.11.12"
    }

    private PlayPlatform resolve(String playPlatform) {
        resolver.resolve(DefaultPlatformRequirement.create(playPlatform))
    }

    private PlayPlatform resolve(Map<String, String> platform) {
        resolver.resolve(new PlayPlatformRequirement(platform['play'], platform['scala'], platform['java']))
    }
}
