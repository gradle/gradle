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
import org.gradle.integtests.fixtures.ToBeFixedForInstantExecution
import org.gradle.integtests.fixtures.executer.ExecutionResult
import org.gradle.scala.ScalaCompilationFixture
import org.gradle.test.fixtures.file.TestFile

class ScalaDocIntegrationTest extends AbstractIntegrationSpec implements DirectoryBuildCacheFixture {

    String scaladoc = ":${ScalaPlugin.SCALA_DOC_TASK_NAME}"
    ScalaCompilationFixture classes = new ScalaCompilationFixture(testDirectory)


    @ToBeFixedForInstantExecution
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

    @ToBeFixedForInstantExecution
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


    @ToBeFixedForInstantExecution
    def "scaladoc uses maxMemory"() {
        buildFile << """
            apply plugin: "scala"
            scaladoc.maxMemory = '234M'

            repositories {
                mavenCentral()
            }

            dependencies {
                implementation 'org.scala-lang:scala-library:2.11.12'
            }
        """

        writeSourceFile()
        when:
        ExecutionResult result = run scaladoc, "-d"

        then:
        //Whole line would look like: [DEBUG] [org.gradle.workers.internal.WorkerDaemonStarter] Starting Gradle worker daemon with fork options DaemonForkOptions{executable=/path/to/java, minHeapSize=null, maxHeapSize=234M, jvmArgs=[], keepAliveMode=DAEMON}
        outputContains("maxHeapSize=234M")
    }

    private TestFile writeSourceFile() {
        file("src/main/scala/Foo.scala") << "class Foo(var x: Int = 0, var y: Int = 0)"
    }
}
