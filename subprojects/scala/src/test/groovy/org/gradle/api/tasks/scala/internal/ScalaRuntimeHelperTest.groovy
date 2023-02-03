/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.api.tasks.scala.internal

import spock.lang.Specification

class ScalaRuntimeHelperTest extends Specification {
    def "allows to find Scala Jar on class path"() {
        when:
        def file = ScalaRuntimeHelper.findScalaJar([new File("other.jar"), new File("scala-jdbc-1.5.jar"), new File("scala-compiler-1.7.jar")], "jdbc")

        then:
        file.name == "scala-jdbc-1.5.jar"
    }

    def "returns null if Scala Jar not found"() {
        when:
        def file = ScalaRuntimeHelper.findScalaJar([new File("other.jar"), new File("scala-jdbc-1.5.jar"), new File("scala-compiler-1.7.jar")], "library")

        then:
        file == null
    }

    def "allows to determine version of Scala Jar"() {
        expect:
        with(ScalaRuntimeHelper) {
            getScalaVersion(new File("scala-compiler-2.9.2.jar")) == "2.9.2"
            getScalaVersion(new File("scala-jdbc-2.9.2.jar")) == "2.9.2"
            getScalaVersion(new File("scala-library-2.10.0-SNAPSHOT.jar")) == "2.10.0-SNAPSHOT"
            getScalaVersion(new File("scala-library-2.10.0-rc-3.jar")) == "2.10.0-rc-3"
        }
    }

    def "returns null if Scala version cannot be determined"() {
        expect:
        with(ScalaRuntimeHelper) {
            getScalaVersion(new File("scala-compiler.jar")) == null
            getScalaVersion(new File("groovy-compiler-2.1.0.jar")) == null
        }
    }
}
