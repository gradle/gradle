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

import org.gradle.api.JavaVersion
import spock.lang.Specification

class DefaultPlayToolChainTest extends Specification {

    def "provides meaningful name"() {
        given:
        def toolChain = new DefaultPlayToolChain(playVersion, "2.11.1", JavaVersion.current())

        expect:
        toolChain.getName() == "PlayFramework$playVersion"

        where:
        playVersion << ["2.3.2", "2.3.5"]
    }

    def "provides meaningful displayname"() {
        given:
        def toolChain = new DefaultPlayToolChain(playVersion, scalaVersion, javaVersion)
        expect:
        toolChain.getDisplayName() == expectedOutput

        where:
        playVersion | scalaVersion | javaVersion             | expectedOutput
        "2.3.2"     | "2.10.2"     | JavaVersion.VERSION_1_6 | "Play Framework 2.3.2 (Scala 2.10.2 / JDK 6 (1.6))"
        "2.3.5"     | "2.10.2"     | JavaVersion.VERSION_1_7 | "Play Framework 2.3.5 (Scala 2.10.2 / JDK 7 (1.7))"
        "2.3.5"     | "2.11.1"     | JavaVersion.VERSION_1_8 | "Play Framework 2.3.5 (Scala 2.11.1 / JDK 8 (1.8))"
    }
}
