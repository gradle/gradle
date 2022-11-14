/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.scala.scaladoc

import org.gradle.api.plugins.scala.ScalaPlugin
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.integtests.fixtures.DirectoryBuildCacheFixture
import org.gradle.integtests.fixtures.jvm.JavaToolchainFixture
import org.gradle.scala.ScalaCompilationFixture

import static org.gradle.api.JavaVersion.VERSION_11
import static org.gradle.api.JavaVersion.VERSION_1_8

class ScalaDocIntegrationTest extends AbstractIntegrationSpec implements DirectoryBuildCacheFixture, JavaToolchainFixture {

    String scaladoc = ":${ScalaPlugin.SCALA_DOC_TASK_NAME}"
    ScalaCompilationFixture classes = new ScalaCompilationFixture(testDirectory)

    def "changing the Scala version makes Scaladoc out of date"() {
        classes.baseline()
        buildScript(classes.buildScript())
        def newScalaVersion = '2.12.6'

        when:
        succeeds scaladoc
        then:
        executedAndNotSkipped scaladoc

        when:
        succeeds scaladoc
        then:
        skipped scaladoc
        newScalaVersion != this.classes.scalaVersion

        when:
        this.classes.scalaVersion = newScalaVersion
        buildScript(this.classes.buildScript())
        succeeds scaladoc
        then:
        executedAndNotSkipped scaladoc
    }

    def "scaladoc is loaded from cache"() {
        classes.baseline()
        buildScript(classes.buildScript())

        when:
        withBuildCache().run scaladoc

        then:
        executedAndNotSkipped scaladoc

        when:
        succeeds 'clean'
        withBuildCache().run scaladoc

        then:
        skipped scaladoc
    }

    def "scaladoc uses maxMemory"() {
        classes.baseline()
        buildScript(classes.buildScript())
        buildFile << """
            scaladoc.maxMemory = '234M'
        """
        when:
        succeeds scaladoc, "-i"

        then:
        // Looks like
        // Started Gradle worker daemon (0.399 secs) with fork options DaemonForkOptions{executable=/Library/Java/JavaVirtualMachines/adoptopenjdk-11.jdk/Contents/Home/bin/java, minHeapSize=null, maxHeapSize=234M, jvmArgs=[], keepAliveMode=DAEMON}.
        outputContains("maxHeapSize=234M")
    }

    def "scaladoc uses scala3"() {
        classes.baseline()
        classes.scalaVersion = '3.0.1'
        given:
        buildScript(classes.buildScript())

        when:
        succeeds scaladoc

        then:
        executedAndNotSkipped scaladoc, ":compileScala"
        file("build/docs/scaladoc/api/_empty_").assertHasDescendants("House.html", "Other.html", "Person.html")
    }

    def 'scaladoc multi project scala 3'() {
        classes.baseline()
        classes.scalaVersion = '3.0.1'
        given:
        settingsFile << """
include(':utils')
"""
        buildScript(classes.buildScript())

        def utilsDir = file('utils')
        def utilsClasses = new ScalaCompilationFixture(utilsDir)
        utilsClasses.scalaVersion = '3.0.1'

        utilsDir.file('build.gradle').text = utilsClasses.buildScript()
        utilsClasses.extra()

        when:
        succeeds scaladoc

        then:
        executedAndNotSkipped scaladoc, ":compileScala"
        file("build/docs/scaladoc/api/_empty_").assertHasDescendants("House.html", "Other.html", "Person.html")
    }

    def "can exclude classes from Scaladoc generation with scala2"() {
        classes.baseline()
        buildScript(classes.buildScript())

        when:
        succeeds scaladoc

        then:
        file("build/docs/scaladoc/Person.html").assertExists()
        file("build/docs/scaladoc/House.html").assertExists()
        file("build/docs/scaladoc/Other.html").assertExists()

        when:
        buildFile << """
scaladoc {
    exclude '**/Other.*'
}
        """
        and:
        succeeds scaladoc

        then:
        file("build/docs/scaladoc/Person.html").assertExists()
        file("build/docs/scaladoc/House.html").assertExists()
        file("build/docs/scaladoc/Other.html").assertDoesNotExist()
    }

    def "can exclude classes from Scaladoc generation with scala3"() {
        classes.scalaVersion = '3.0.1'
        classes.baseline()
        buildScript(classes.buildScript())

        when:
        succeeds scaladoc

        then:
        file("build/docs/scaladoc/api/_empty_").assertHasDescendants("House.html", "Other.html", "Person.html")

        when:
        buildFile << """
scaladoc {
    exclude '**/Other.*'
}
        """
        and:
        succeeds scaladoc

        then:
        file("build/docs/scaladoc/api/_empty_").assertHasDescendants("House.html", "Person.html")
    }

    def "scaladoc is out of date when changing the java launcher"() {
        def jdk8 = AvailableJavaHomes.getJvmInstallationMetadata(AvailableJavaHomes.getJdk(VERSION_1_8))
        def jdk11 = AvailableJavaHomes.getJvmInstallationMetadata(AvailableJavaHomes.getJdk(VERSION_11))

        classes.baseline()
        buildScript(classes.buildScript())

        buildFile << """
            tasks.withType(ScalaDoc) {
                javaLauncher = javaToolchains.launcherFor {
                    languageVersion = JavaLanguageVersion.of(
                        !providers.gradleProperty("changed").isPresent()
                            ? ${jdk8.languageVersion.majorVersion}
                            : ${jdk11.languageVersion.majorVersion}
                    )
                }
            }
        """

        when:
        withInstallations(jdk8, jdk11).run scaladoc

        then:
        executedAndNotSkipped scaladoc

        when:
        withInstallations(jdk8, jdk11).run scaladoc
        then:
        skipped scaladoc

        when:
        withInstallations(jdk8, jdk11).run scaladoc, '-Pchanged', '--info'
        then:
        executedAndNotSkipped scaladoc
        outputContains("Value of input property 'javaLauncher.metadata.taskInputs.languageVersion' has changed for task '${scaladoc}'")

        when:
        withInstallations(jdk8, jdk11).run scaladoc, '-Pchanged', '--info'
        then:
        skipped scaladoc
    }
}
