/*
 * Copyright 2011 the original author or authors.
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

package org.gradle.integtests.environment

import org.gradle.internal.os.OperatingSystem
import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.util.Jvm
import org.gradle.internal.nativeplatform.FileSystems

import spock.lang.Issue
import org.gradle.util.TextUtil
import org.gradle.integtests.fixtures.AbstractIntegrationSpec

/**
 * @author: Szczepan Faber, created at: 8/11/11
 */
class BuildEnvironmentIntegrationTest extends AbstractIntegrationSpec {
    def "canonicalizes working directory"() {
        distribution.testFile("java/multiproject").createDir()
        def projectDir = distribution.testFile("java/quickstart")
        projectDir.file('build.gradle') << "assert file('.') == new File(new URI('${projectDir.toURI()}'))"

        when:
        File relativeDir = new File(distribution.testDir, 'java/multiproject/../quickstart')
        executer.inDirectory(relativeDir).run()

        if (!FileSystems.default.caseSensitive) {
            File mixedCaseDir = new File(distribution.testDir, "JAVA/QuickStart")
            executer.inDirectory(mixedCaseDir).run()
        }
        if (OperatingSystem.current().isWindows()) {
            File shortDir = new File(distribution.testDir, 'java/QUICKS~1')
            executer.inDirectory(shortDir).run()
        }

        then:
        noExceptionThrown()
    }

    @Issue("GRADLE-1762")
    def "build uses environment variables from where the build was launched"() {
        file('build.gradle') << "println System.getenv('foo')"

        when:
        def out = executer.withEnvironmentVars(foo: "gradle rocks!").run().output

        then:
        out.contains("gradle rocks!")

        when:
        out = executer.withEnvironmentVars(foo: "and will be even better").run().output

        then:
        out.contains("and will be even better")
    }

    def "build is executed with working directory set to where the build was launched from"() {
        def project1 = distribution.testFile("project1")
        def project2 = distribution.testFile("project2")

        project1.file('build.gradle') << """
def expectedDir = new File(new URI('${project1.toURI()}'))
def dir = new File('.')
assert dir.canonicalFile == expectedDir.canonicalFile
assert dir.directory
def classesDir = new File("build/classes1")
assert classesDir.mkdirs()
assert classesDir.directory
"""

        project2.file('build.gradle') << """
def expectedDir = new File(new URI('${project2.toURI()}'))
def dir = new File('.')
assert dir.canonicalFile == expectedDir.canonicalFile
assert dir.directory
def classesDir = new File("build/classes2")
assert classesDir.mkdirs()
assert classesDir.directory
"""

        when:
        executer.inDirectory(project1).run()
        executer.inDirectory(project2).run()

        then:
        noExceptionThrown()
    }

    def "system properties should be made available to build"() {
        file('build.gradle') << "assert System.properties['foo'] == 'bar'"

        when:
        executer.withArguments("-Dfoo=bar").run()

        then:
        noExceptionThrown()
    }

    def "java home from environment should be used to run build"() {
        def alternateJavaHome = AvailableJavaHomes.bestAlternative
        if (alternateJavaHome == null) {
            return
        }

        file('build.gradle') << "println 'javaHome=' + org.gradle.util.Jvm.current().javaHome.canonicalPath"

        when:
        def out = executer.run().output

        then:
        out.contains("javaHome=" + Jvm.current().javaHome.canonicalPath)

        when:
        out = executer.withJavaHome(alternateJavaHome).run().output

        then:
        out.contains("javaHome=" + alternateJavaHome.canonicalPath)
    }

    def "java home from gradle properties should be used to run build"() {
        def alternateJavaHome = AvailableJavaHomes.bestAlternative
        if (alternateJavaHome == null) {
            return
        }

        file('gradle.properties') << "org.gradle.java.home=${TextUtil.escapeString(alternateJavaHome.canonicalPath)}"

        file('build.gradle') << "println 'javaHome=' + org.gradle.util.Jvm.current().javaHome.absolutePath"

        when:
        // Need the forking executer for this to work. Embedded executer will not fork a new process if jvm doesn't match.
        def out = executer.withForkingExecuter().run().output

        then:
        out.contains("javaHome=" + alternateJavaHome.absolutePath)
    }
}
