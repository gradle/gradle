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

import org.gradle.api.GradleException
import org.gradle.api.artifacts.Configuration
import org.gradle.api.plugins.scala.ScalaBasePlugin
import org.gradle.language.scala.fixtures.ScalaJarFactory
import org.gradle.test.fixtures.AbstractProjectBuilderSpec

class ScalaRuntimeTest extends AbstractProjectBuilderSpec {

    private ScalaJarFactory scalaJarFactory
    private ScalaRuntime scalaRuntime

    def setup() {
        scalaJarFactory = new ScalaJarFactory(temporaryFolder.testDirectory)
        project.pluginManager.apply(ScalaBasePlugin)
        scalaRuntime = project.scalaRuntime
    }

    def "inferred Scala class path contains 'scala-compiler' repository dependency and 'compiler-bridge' matching 'scala-library' Jar found on class path"() {
        project.repositories {
            mavenCentral()
        }
        when:
        def configuration = scalaRuntime.registerScalaClasspathConfigurationFor("test", "case", "2.10.1")
        def classpath = scalaRuntime.inferScalaClasspath([new File("other.jar"), scalaJarFactory.standard("library", "2.10.1")])
        then:
        configuration.name == "scalaClasspathForTestCase"
        assertHasCorrectScala2Dependencies(configuration.get(), ScalaBasePlugin.DEFAULT_ZINC_VERSION)
        assertHasCorrectScala2Dependencies(classpath, ScalaBasePlugin.DEFAULT_ZINC_VERSION)
    }

    def "inferred Scala class path contains 'scala-compiler' repository dependency and 'compiler-bridge' matching 'scala-library' Jar found on class path with specified zinc version"() {
        project.repositories {
            mavenCentral()
        }
        def useZincVersion = "1.3.4"
        project.scala {
            zincVersion = useZincVersion
        }
        when:
        def configuration = scalaRuntime.registerScalaClasspathConfigurationFor("test", "case", "2.10.1")
        def classpath = scalaRuntime.inferScalaClasspath([new File("other.jar"), scalaJarFactory.standard("library", "2.10.1")])
        then:
        configuration.name == "scalaClasspathForTestCase"
        assertHasCorrectScala2Dependencies(configuration.get(), useZincVersion)
        assertHasCorrectScala2Dependencies(classpath, useZincVersion)
    }

    private void assertHasCorrectScala2Dependencies(classpath, zincVersion) {
        assert classpath instanceof Configuration
        assert classpath.state == Configuration.State.UNRESOLVED
        assert classpath.dependencies.size() == 3
        assert classpath.dependencies.any { d ->
            d.group == "org.scala-lang" &&
                d.name == "scala-compiler" &&
                d.version == "2.10.1"
        }
        assert classpath.dependencies.any { d ->
            d.group == "org.scala-sbt" &&
                d.name == "compiler-bridge_2.10" &&
                d.version == zincVersion
        }
        assert classpath.dependencies.any { d ->
            d.group == "org.scala-sbt" &&
                d.name == "compiler-interface" &&
                d.version == zincVersion
        }
    }

    def "inferred Scala class path contains 'scala3-compiler_3' repository dependency and 'compiler-bridge' matching 'scala-library' Jar found on class path"() {
        project.repositories {
            mavenCentral()
        }
        when:
        def configuration = scalaRuntime.registerScalaClasspathConfigurationFor("test", "case", "3.0.1")
        def classpath = scalaRuntime.inferScalaClasspath([new File("other.jar"), scalaJarFactory.standard("library", "3.0.1")])
        then:
        configuration.name == "scalaClasspathForTestCase"
        assertHasCorrectScala3Dependencies(configuration.get())
        assertHasCorrectScala3Dependencies(classpath)
    }


    private void assertHasCorrectScala3Dependencies(classpath) {
        assert classpath instanceof Configuration
        assert classpath.state == Configuration.State.UNRESOLVED
        assert classpath.dependencies.size() == 4
        assert classpath.dependencies.any { d ->
            d.group == "org.scala-lang" &&
                d.name == "scala3-compiler_3" &&
                d.version == "3.0.1"
        }
        assert classpath.dependencies.any { d ->
            d.group == "org.scala-lang" &&
                d.name == "scala3-sbt-bridge" &&
                d.version == "3.0.1"
        }
        assert classpath.dependencies.any { d ->
            d.group == "org.scala-lang" &&
                d.name == "scala3-interfaces" &&
                d.version == "3.0.1"
        }
        assert classpath.dependencies.any { d ->
            d.group == "org.scala-lang" &&
                d.name == "scaladoc_3" &&
                d.version == "3.0.1"
        }
    }

    def "inference fails if 'scalaTools' configuration is empty and no repository declared"() {
        when:
        def scalaClasspath = scalaRuntime.inferScalaClasspath([new File("other.jar"), scalaJarFactory.standard("library", "2.10.1")])
        scalaClasspath.files

        then:
        GradleException e = thrown()
        e.message == "Could not resolve all files for configuration ':detachedConfiguration1'."
        e.cause.message.startsWith("Cannot resolve external dependency org.scala-lang:scala-compiler:2.10.1 because no repositories are defined.")
    }

    def "inference fails if 'scalaTools' configuration is empty and no Scala library Jar is found on class path"() {
        project.repositories {
            mavenCentral()
        }

        when:
        def scalaClasspath = scalaRuntime.inferScalaClasspath([new File("other.jar"), new File("other2.jar")])
        scalaClasspath.files

        then:
        GradleException e = thrown()
        e.message == "Could not resolve all dependencies for configuration ':detachedConfiguration1'."
        e.cause.message.startsWith("Cannot infer Scala version because no Scala Library JAR was found. Does root project 'test-project' declare a dependency on scala-library? Searched classpath: ")
    }

    def "allows to find Scala Jar on class path"() {
        when:
        def file = scalaRuntime.findScalaJar([new File("other.jar"), scalaJarFactory.standard("jdbc", "1.5"), scalaJarFactory.standard("compiler", "1.7")], "jdbc")

        then:
        file.name == "scala-jdbc-1.5.jar"
    }

    def "returns null if Scala Jar not found"() {
        when:
        def file = scalaRuntime.findScalaJar([new File("other.jar"), scalaJarFactory.standard("jdbc", "1.5"), scalaJarFactory.standard("compiler", "1.7")], "library")

        then:
        file == null
    }

    def "allows to determine version of Scala Jar"() {
        expect:
        scalaRuntime.getScalaVersion(scalaJarFactory.standard("compiler", "3.4.0")) == "3.4.0"
        scalaRuntime.getScalaVersion(scalaJarFactory.standard("compiler", "2.9.2")) == "2.9.2"
        scalaRuntime.getScalaVersion(scalaJarFactory.standard("jdbc", "2.9.2")) == "2.9.2"
        scalaRuntime.getScalaVersion(scalaJarFactory.standard("library", "2.10.0-SNAPSHOT")) == "2.10.0-SNAPSHOT"
        scalaRuntime.getScalaVersion(scalaJarFactory.standard("library", "2.10.0-rc-3")) == "2.10.0-rc-3"
    }

    def "returns null if Scala version cannot be determined"() {
        expect:
        scalaRuntime.getScalaVersion(scalaJarFactory.custom("library", false, null, null, null)) == null
        scalaRuntime.getScalaVersion(scalaJarFactory.custom("compiler", false, null, null, null)) == null
        scalaRuntime.getScalaVersion(new File("groovy-compiler-2.1.0.jar")) == null
    }

    def "allows to correctly extract the Scala version from a standard classpath"() {
        def cp3x4x0 = [
            new File("other.jar"),
            scalaJarFactory.standard("library", "3.4.0"),
            new File("another.jar"),
            scalaJarFactory.standard("library", "2.13.12"),
            scalaJarFactory.standard("reflect", "2.13.9"),
        ]
        def cp2x13x12 = cp3x4x0.findAll {!it.name.startsWith("scala3-") }

        expect:
        scalaRuntime.findScalaVersion(cp3x4x0) == "3.4.0"
        scalaRuntime.getScalaVersion(cp3x4x0) == "3.4.0"
        scalaRuntime.findScalaVersion(cp3x4x0.reverse()) == "3.4.0"
        scalaRuntime.getScalaVersion(cp3x4x0.reverse()) == "3.4.0"

        scalaRuntime.findScalaVersion(cp2x13x12) == "2.13.12"
        scalaRuntime.getScalaVersion(cp2x13x12) == "2.13.12"
        scalaRuntime.findScalaVersion(cp2x13x12.reverse()) == "2.13.12"
        scalaRuntime.getScalaVersion(cp2x13x12.reverse()) == "2.13.12"
    }

    def "allows to correctly extract the Scala version from a classpath with unusual but acceptable files"() {
        def cp3x4x0 = [
            new File("other.jar"),
            scalaJarFactory.custom("library", true, "3.x", null, "3.4.0"),
            new File("another.jar"),
            scalaJarFactory.custom("library", false, "2.x", "2.13.12", null),
            scalaJarFactory.standard("reflect", "2.13.9"),
        ]
        def cp2x13x12 = cp3x4x0.findAll {!it.name.startsWith("scala3-") }

        def cp3x3x2 = [
            new File("other.jar"),
            scalaJarFactory.custom("library", true, null, null, "3.3.2"),
            scalaJarFactory.custom("library", false, null, "2.13.5", null),
            scalaJarFactory.custom("library", false, "2.13.0", null, null),
        ]
        def cp2x13x5 = cp3x3x2.findAll {!it.name.startsWith("scala3-") }
        def cp2x13x0 = cp2x13x5.findAll {it.name != "scala-library.jar" }

        expect:
        scalaRuntime.findScalaVersion(cp3x4x0) == "3.4.0"
        scalaRuntime.getScalaVersion(cp3x4x0) == "3.4.0"
        scalaRuntime.findScalaVersion(cp3x4x0.reverse()) == "3.4.0"
        scalaRuntime.getScalaVersion(cp3x4x0.reverse()) == "3.4.0"

        scalaRuntime.findScalaVersion(cp2x13x12) == "2.13.12"
        scalaRuntime.getScalaVersion(cp2x13x12) == "2.13.12"
        scalaRuntime.findScalaVersion(cp2x13x12.reverse()) == "2.13.12"
        scalaRuntime.getScalaVersion(cp2x13x12.reverse()) == "2.13.12"

        scalaRuntime.findScalaVersion(cp3x3x2) == "3.3.2"
        scalaRuntime.getScalaVersion(cp3x3x2) == "3.3.2"
        scalaRuntime.findScalaVersion(cp3x3x2.reverse()) == "3.3.2"
        scalaRuntime.getScalaVersion(cp3x3x2.reverse()) == "3.3.2"

        scalaRuntime.findScalaVersion(cp2x13x5) == "2.13.5"
        scalaRuntime.getScalaVersion(cp2x13x5) == "2.13.5"
        scalaRuntime.findScalaVersion(cp2x13x5.reverse()) == "2.13.5"
        scalaRuntime.getScalaVersion(cp2x13x5.reverse()) == "2.13.5"

        scalaRuntime.findScalaVersion(cp2x13x0) == "2.13.0"
        scalaRuntime.getScalaVersion(cp2x13x0) == "2.13.0"
        scalaRuntime.findScalaVersion(cp2x13x0.reverse()) == "2.13.0"
        scalaRuntime.getScalaVersion(cp2x13x0.reverse()) == "2.13.0"
    }

    def "fails to extract the Scala version from a classpath without a valid Scala library"() {
        def cpNone = [
            new File("other.jar"),
            new File("scala3-library_3-3.4.0.jar"),
            new File("scala-library-2.13.12.jar"),
            scalaJarFactory.custom("library", true, null, null, null),
            scalaJarFactory.custom("library", false, null, null, null),
            scalaJarFactory.standard("compiler", "3.4.0"),
            scalaJarFactory.standard("reflect", "2.13.12"),
        ]

        expect:
        scalaRuntime.findScalaVersion(cpNone) == null

        when:
        scalaRuntime.getScalaVersion(cpNone)

        then:
        Exception e = thrown()
        e instanceof GradleException
        e.message.startsWith("Cannot infer Scala version because no Scala Library JAR was found. Does root project 'test-project' declare a dependency on scala-library? Searched classpath: ")
    }
}
