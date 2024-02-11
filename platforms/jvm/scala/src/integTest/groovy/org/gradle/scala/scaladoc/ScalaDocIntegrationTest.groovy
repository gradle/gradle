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
import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.integtests.fixtures.DirectoryBuildCacheFixture
import org.gradle.integtests.fixtures.MultiVersionIntegrationSpec
import org.gradle.integtests.fixtures.ScalaCoverage
import org.gradle.integtests.fixtures.TargetCoverage
import org.gradle.integtests.fixtures.jvm.JavaToolchainFixture
import org.gradle.scala.ScalaCompilationFixture

import static org.gradle.api.JavaVersion.VERSION_11
import static org.gradle.api.JavaVersion.VERSION_1_8

@TargetCoverage({ ScalaCoverage.SUPPORTED_BY_JDK })
class ScalaDocIntegrationTest extends MultiVersionIntegrationSpec implements DirectoryBuildCacheFixture, JavaToolchainFixture {

    String scaladoc = ":${ScalaPlugin.SCALA_DOC_TASK_NAME}"
    ScalaCompilationFixture classes = new ScalaCompilationFixture(testDirectory)

    def getOtherScalaVersion() {
        def currentScalaVersion = version.toString()
        return ScalaCoverage.SUPPORTED_BY_JDK.find { it != currentScalaVersion }
    }

    def getDocsPath() {
        return classes.isScala3() ? "build/docs/scaladoc/_empty_" : "build/docs/scaladoc"
    }

    def setup() {
        classes.scalaVersion = version.toString()
    }

    def "scaladoc produces output"() {
        classes.baseline()
        buildScript(classes.buildScript())

        when:
        succeeds scaladoc

        then:
        executedAndNotSkipped scaladoc
        file(docsPath).assertContainsDescendants("House.html", "Other.html", "Person.html")
    }

    def "changing the Scala version makes Scaladoc out of date"() {
        def newScalaVersion = getOtherScalaVersion()

        classes.baseline()
        buildScript(classes.buildScript())

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
        succeeds "clean"
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

    def "scaladoc multi project"() {
        classes.baseline()
        settingsFile << """
            include(':utils')
        """
        buildScript(classes.buildScript())

        def utilsDir = file("utils")
        def utilsClasses = new ScalaCompilationFixture(utilsDir)
        utilsClasses.scalaVersion = version.toString()

        utilsDir.file("build.gradle").text = utilsClasses.buildScript()
        utilsClasses.extra()

        when:
        succeeds "scaladoc" // intentionally non-qualified

        then:
        executedAndNotSkipped scaladoc
        file(docsPath).assertContainsDescendants("House.html", "Other.html", "Person.html")
        file("utils/$docsPath").assertContainsDescendants("City.html")
    }

    def "can exclude classes from Scaladoc generation"() {
        classes.baseline()
        buildScript(classes.buildScript())

        when:
        succeeds scaladoc

        then:
        file("${docsPath}/Person.html").assertExists()
        file("${docsPath}/House.html").assertExists()
        file("${docsPath}/Other.html").assertExists()

        when:
        buildFile << """
            scaladoc {
                exclude '**/Other.*'
            }
        """
        and:
        succeeds scaladoc

        then:
        file("${docsPath}/Person.html").assertExists()
        file("${docsPath}/House.html").assertExists()
        file("${docsPath}/Other.html").assertDoesNotExist()
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
