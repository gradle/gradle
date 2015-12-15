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

package org.gradle.language.scala

import groovy.transform.NotYetImplemented
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.integtests.fixtures.jvm.TestJvmComponent
import org.gradle.integtests.language.AbstractJvmLanguageIntegrationTest
import org.gradle.language.scala.fixtures.BadScalaLibrary
import org.gradle.language.scala.fixtures.TestScalaComponent
import spock.lang.IgnoreIf
import spock.lang.Issue

class ScalaLanguageIntegrationTest extends AbstractJvmLanguageIntegrationTest {
    TestJvmComponent app = new TestScalaComponent()

    def "reports failure to compile bad scala sources"() {
        when:
        def badApp = new BadScalaLibrary()
        badApp.sources*.writeToDir(file("src/myLib/scala"))

        and:
        buildFile << """
    model {
        components {
            myLib(JvmLibrarySpec)
        }
    }
"""
        then:
        fails "assemble"

        and:
        badApp.compilerErrors.each {
            assert errorOutput.contains(it)
        }
    }

    @Issue("GRADLE-3371")
    @Issue("GRADLE-3370")
    @NotYetImplemented
    @IgnoreIf({GradleContextualExecuter.parallel}) // this test is always parallel
    def "multi-project build is multi-process safe"() {
        given:
        def projects = (1..4)
        projects.each {
            def projectName = "project$it"
            def projectDir = testDirectory.file(projectName)
            def buildFile = projectDir.file("build.gradle")
            def srcDir = projectDir.file("src/main/scala/org/${projectName}")
            def sourceFile = srcDir.file("Main.scala")
            srcDir.mkdirs()
            buildFile << """
    plugins {
        id 'jvm-component'
        id 'scala-lang'
    }
    repositories{
        mavenCentral()
    }
    model {
        components {
            main(JvmLibrarySpec)
        }
    }
"""
            sourceFile << """
package org.${projectName};
object Main {}
"""
            settingsFile << """
    include '$projectName'
"""
        }
        buildFile.text = """
def startBuild = new java.util.concurrent.CountDownLatch(${projects.size()})
allprojects {
    tasks.withType(PlatformScalaCompile) {
        doFirst {
            logger.lifecycle "\$name is waiting for the compile tasks"
            startBuild.countDown()
            startBuild.await()
        }
        options.forkOptions.jvmArgs += '-Dzinc.dir=${testDirectory}/.zinc'
    }
}
"""
        executer.withArgument("--parallel")
        executer.withArgument("--max-workers=${projects.size()}")
        when:
        succeeds("build")
        then:
        noExceptionThrown()
    }
}
