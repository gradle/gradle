/*
 * Copyright 2013 the original author or authors.
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
package org.gradle.api.tasks

import org.gradle.api.artifacts.Configuration
import org.gradle.api.internal.file.collections.LazilyInitializedFileCollection
import org.gradle.api.plugins.scala.ScalaBasePlugin
import org.gradle.util.HelperUtil

import spock.lang.Specification

class ScalaRuntimeTest extends Specification {
    def project = HelperUtil.createRootProject()

    def setup() {
        project.plugins.apply(ScalaBasePlugin)
    }

    def "inferred Scala class path contains 'scala-compiler' repository dependency matching 'scala-library' Jar found on class path"() {
        project.repositories {
            mavenCentral()
        }

        when:
        def classpath = project.scalaRuntime.inferScalaClasspath([project.file("other.jar"), project.file("scala-library-2.10.0.jar")])

        then:
        classpath instanceof LazilyInitializedFileCollection
        with(classpath.delegate) {
            it instanceof Configuration
            it.state == Configuration.State.UNRESOLVED
            it.dependencies.size() == 1
            with(it.dependencies.iterator().next()) {
                group == "org.scala-lang"
                name == "scala-compiler"
                version == "2.10.0"
            }
        }
    }

    def "inferred Scala class path falls back to contents of 'scalaTools' configuration if no repository declared"() {
        project.dependencies {
            scalaTools project.files("my-scala.jar")
        }

        when:
        def classpath = project.scalaRuntime.inferScalaClasspath([project.file("other.jar"), project.file("scala-library-2.10.0.jar")])

        then:
        classpath.singleFile.name == "my-scala.jar"
    }

    def "inferred Scala class path  falls back to contents of 'scalaTools' configuration if Scala library not found on class path"() {
        project.dependencies {
            scalaTools project.files("my-scala.jar")
        }

        when:
        def classpath = project.scalaRuntime.inferScalaClasspath([project.file("other.jar"), project.file("other2.jar")])

        then:
        classpath.singleFile.name == "my-scala.jar"
    }

    def "allows to find Scala Jar on class path"() {
        when:
        def file = project.scalaRuntime.findScalaJar([project.file("other.jar"), project.file("scala-jdbc-1.5.jar"), project.file("scala-compiler-1.7.jar")], "jdbc")

        then:
        file.name == "scala-jdbc-1.5.jar"
    }

    def "returns null if Scala Jar not found"() {
        when:
        def file = project.scalaRuntime.findScalaJar([project.file("other.jar"), project.file("scala-jdbc-1.5.jar"), project.file("scala-compiler-1.7.jar")], "library")

        then:
        file == null
    }

    def "allows to determine version of Scala Jar"() {
        expect:
        with(project.scalaRuntime) {
            getScalaVersion(project.file("scala-compiler-2.9.2.jar")) == "2.9.2"
            getScalaVersion(project.file("scala-jdbc-2.9.2.jar")) == "2.9.2"
            getScalaVersion(project.file("scala-library-2.10.0-SNAPSHOT.jar")) == "2.10.0-SNAPSHOT"
            getScalaVersion(project.file("scala-library-2.10.0-rc-3.jar")) == "2.10.0-rc-3"
        }
    }

    def "returns null if Scala version cannot be determined"() {
        expect:
        with(project.scalaRuntime) {
            getScalaVersion(project.file("scala-compiler.jar")) == null
            getScalaVersion(project.file("groovy-compiler-2.1.0.jar")) == null
        }
    }
}
