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
import org.gradle.play.platform.PlayPlatform
import spock.lang.Specification

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
        resolve "play-2.1.0"

        then:
        def e = thrown(InvalidUserDataException)
        e.message == "Not a supported Play version: 2.1.0. This plugin is compatible with: 2.3.x, 2.2.x"
    }

    def "resolves platform for Play 2.2.x"() {
        when:
        def playPlatform = resolve "play-2.2.3"

        then:
        playPlatform.name == "play-2.2.3"
        playPlatform.playVersion == "2.2.3"
        playPlatform.javaPlatform.targetCompatibility == JavaVersion.current()
        playPlatform.scalaPlatform.scalaVersion == "2.10.4"
    }

    def "resolves platform for Play 2.3.x"() {
        when:
        def playPlatform = resolve "play-2.3.4"

        then:
        playPlatform.name == "play-2.3.4"
        playPlatform.playVersion == "2.3.4"
        playPlatform.javaPlatform.targetCompatibility == JavaVersion.current()
        playPlatform.scalaPlatform.scalaVersion == "2.11.4"
    }

    private PlayPlatform resolve(String playPlatform) {
        resolver.resolve(DefaultPlatformRequirement.create(playPlatform))
    }
}
