/*
 * Copyright 2015 the original author or authors.
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
package org.gradle.play.integtest
import org.gradle.api.reporting.components.AbstractComponentReportIntegrationTest
import org.gradle.integtests.fixtures.ToBeFixedForInstantExecution
import org.gradle.platform.base.internal.DefaultPlatformRequirement
import org.gradle.play.internal.DefaultPlayPlatform
import org.gradle.play.internal.PlayPlatformResolver

class PlayPlatformComponentReportIntegrationTest extends AbstractComponentReportIntegrationTest {
    private String defaultPlayPlatformName = String.format("play-%s", DefaultPlayPlatform.DEFAULT_PLAY_VERSION);
    private def defaultPlayPlatform = new PlayPlatformResolver().resolve(DefaultPlatformRequirement.create(defaultPlayPlatformName));

    @ToBeFixedForInstantExecution(because = ":components")
    def "shows details of Play application"() {
        executer.expectDeprecationWarnings(6)

        given:
        buildFile << """
            plugins {
                id 'play-application'
            }

            ${mavenCentralRepository()}

            model {
                components {
                    play {
                        targetPlatform "$defaultPlayPlatformName"
                    }
                }
            }
        """

        when:
        succeeds "components"

        then:
        outputMatches """
Play Application 'play'
-----------------------

Source sets
    Java source 'play:java'
        srcDir: app
        includes: **/*.java
    JVM resources 'play:resources'
        srcDir: conf
    Routes source 'play:routes'
        srcDir: conf
        includes: routes, *.routes
    Scala source 'play:scala'
        srcDir: app
        includes: **/*.scala
    Twirl template source 'play:twirlTemplates'
        srcDir: app
        includes: **/*.scala.*

Binaries
    Play Application Jar 'play:binary'
        build using task: :playBinary
        target platform: $defaultPlayPlatform
        toolchain: Default Play Toolchain
        classes dir: build/playBinary/classes
        resources dir: build/playBinary/resources
        JAR file: build/playBinary/lib/test.jar
"""
    }
}
