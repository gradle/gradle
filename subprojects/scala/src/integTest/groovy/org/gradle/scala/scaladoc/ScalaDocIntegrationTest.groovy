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
import org.gradle.integtests.fixtures.DirectoryBuildCacheFixture
import org.gradle.scala.ScalaCompilationFixture
import spock.lang.IgnoreRest

import static org.gradle.integtests.fixtures.RepoScriptBlockUtil.mavenCentralRepository

class ScalaDocIntegrationTest extends AbstractIntegrationSpec implements DirectoryBuildCacheFixture {

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
        given:
        buildFile << """
plugins {
    id 'scala'
}

${mavenCentralRepository()}

dependencies {
    implementation 'org.scala-lang:scala3-library_3:3.0.1'
}

"""
        when:
        succeeds scaladoc

        then:
        executedAndNotSkipped scaladoc, ":compileScala"
    }

    @IgnoreRest
    def 'scaladoc multi project scala 3'() {
        classes.baseline()
        given:
        settingsFile << """
include(':utils')
"""
        buildFile << """
plugins {
    id 'java-library'
    id 'scala'
}

${mavenCentralRepository()}

dependencies {
    implementation 'org.scala-lang:scala3-library_3:3.0.1'
    implementation project(':utils')
}

"""
        def utilsDir = testDirectory.createDir('utils')
        utilsDir.createFile('build.gradle') << """
plugins {
    id 'java-library'
    id 'scala'
}

${mavenCentralRepository()}

dependencies {
    implementation 'org.scala-lang:scala3-library_3:3.0.1'
}
"""
        def utilsClasses = new ScalaCompilationFixture(utilsDir)
        utilsClasses.extra()

        when:
        succeeds scaladoc

        then:
        executedAndNotSkipped scaladoc, ":compileScala"
        testDirectory.file("build/docs/scaladoc/api/_empty_").assertHasDescendants("House.html", "Other.html", "Person.html")
    }
}
