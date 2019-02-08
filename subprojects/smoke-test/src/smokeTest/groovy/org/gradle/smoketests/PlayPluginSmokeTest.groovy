/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.smoketests

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

class PlayPluginSmokeTest extends AbstractSmokeTest {
    def 'build basic Play project'() {
        given:
        useSample("play-example")
        buildFile << """
            plugins {
                id 'org.gradle.playframework' version '${TestedVersions.play}'
            }
            
            repositories {
                ${jcenterRepository()}
                maven {
                    name "lightbend-maven-release"
                    url "https://repo.lightbend.com/lightbend/maven-releases"
                }
                ivy {
                    name "lightbend-ivy-release"
                    url "https://repo.lightbend.com/lightbend/ivy-releases"
                    layout "ivy"
                }
            }
            
            dependencies {
                play 'commons-lang:commons-lang:2.6'
                playTest "com.google.guava:guava:17.0"
                playTest "org.scalatestplus.play:scalatestplus-play_2.12:3.1.2"
                play "com.typesafe.play:play-guice_2.12:2.6.15"
                play "ch.qos.logback:logback-classic:1.2.3"
            }
        """

        when:
        def result = runner('build').build()

        then:
        result.task(':build').outcome == SUCCESS
    }
}
