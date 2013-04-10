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
import org.gradle.api.plugins.scala.ScalaBasePlugin
import org.gradle.util.HelperUtil

import spock.lang.Specification

class ScalaRuntimeTest extends Specification {
    def project = HelperUtil.createRootProject()

    def "allows to infer Scala compiler class path"() {
        project.plugins.apply(ScalaBasePlugin)

        project.repositories {
            mavenCentral()
        }

        when:
        def classpath = project.scalaRuntime.inferScalaClasspath([new File("other.jar"), new File("scala-library-2.10.0.jar")])

        then:
        classpath instanceof Configuration
        classpath.state == Configuration.State.UNRESOLVED
        classpath.dependencies.size() == 1
        with(classpath.dependencies.iterator().next()) {
            group == "org.scala-lang"
            name == "scala-compiler"
            version == "2.10.0"
        }
    }

    def "inference falls back to scalaTools if no repository declared"() {
        project.plugins.apply(ScalaBasePlugin)

        when:
        def classpath = project.scalaRuntime.inferScalaClasspath([new File("other.jar"), new File("scala-library-2.10.0.jar")])

        then:
        classpath == project.configurations.scalaTools
    }

    def "inference falls back to scalaTools if Scala library not found on class path"() {
        project.plugins.apply(ScalaBasePlugin)

        when:
        def classpath = project.scalaRuntime.inferScalaClasspath([new File("other.jar"), new File("other2.jar")])

        then:
        classpath == project.configurations.scalaTools
    }

    def "allows to find Scala Jar on class path"() {
        project.plugins.apply(ScalaBasePlugin)

        when:
        def file = project.scalaRuntime.findScalaJar([new File("other.jar"), new File("scala-jdbc-1.5.jar"), new File("scala-compiler-1.7.jar")], "jdbc")

        then:
        file.name == "scala-jdbc-1.5.jar"
    }

    def "returns null if Scala Jar not found"() {
        project.plugins.apply(ScalaBasePlugin)

        when:
        def file = project.scalaRuntime.findScalaJar([new File("other.jar"), new File("scala-jdbc-1.5.jar"), new File("scala-compiler-1.7.jar")], "library")

        then:
        file == null
    }

    def "allows to determine version of Scala Jar"() {
        project.plugins.apply(ScalaBasePlugin)

        expect:
        with(project.scalaRuntime) {
            getScalaVersion(new File("scala-compiler-2.9.2.jar")) == "2.9.2"
            getScalaVersion(new File("scala-jdbc-2.9.2.jar")) == "2.9.2"
            getScalaVersion(new File("scala-library-2.10.0-SNAPSHOT.jar")) == "2.10.0-SNAPSHOT"
            getScalaVersion(new File("scala-library-2.10.0-rc-3.jar")) == "2.10.0-rc-3"
        }
    }

    def "returns null if Scala version cannot be determined"() {
        project.plugins.apply(ScalaBasePlugin)

        expect:
        with(project.scalaRuntime) {
            getScalaVersion(new File("scala-compiler.jar")) == null
            getScalaVersion(new File("groovy-compiler-2.1.0.jar")) == null
        }
    }
}
