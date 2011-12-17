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

import org.gradle.integtests.fixtures.internal.AbstractIntegrationTest
import org.gradle.os.OperatingSystem
import org.gradle.testing.AvailableJavaHomes
import org.gradle.util.Jvm
import org.gradle.os.FileSystems

import org.junit.Test
import spock.lang.Issue

/**
 * @author: Szczepan Faber, created at: 8/11/11
 */
class BuildEnvironmentIntegrationTest extends AbstractIntegrationTest {

    @Test
    public void "canonicalizes working directory"() {
        distribution.testFile("java/multiproject").createDir()
        def projectDir = distribution.testFile("java/quickstart")
        projectDir.file('build.gradle') << """
assert file('.') == new File(new URI('${projectDir.toURI()}'))
"""
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
    }

    @Issue("GRADLE-1762")
    @Test
    void "build uses environment variables from where the build was launched"() {
        file('build.gradle') << """
println System.getenv('foo')
"""

        def out = executer.withEnvironmentVars(foo: "gradle rocks!").run().output
        assert out.contains("gradle rocks!")

        out = executer.withEnvironmentVars(foo: "and will be even better").run().output
        assert out.contains("and will be even better")
    }

    @Test
    public void "build is executed with working directory set to where the build was launched from"() {
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

        executer.inDirectory(project1).run()
        executer.inDirectory(project2).run()
    }

    @Test
    void "system properties should be made available to build"() {
        file('build.gradle') << """
    assert System.properties['foo'] == 'bar'
"""
        executer.withArguments("-Dfoo=bar").run()
    }

    @Test
    void "specified java home should be used to run build"() {
        def alternateJavaHome = AvailableJavaHomes.bestAlternative
        if (alternateJavaHome == null) {
            return
        }

        file('build.gradle') << """
            println "javaHome=" + org.gradle.util.Jvm.current().javaHome.canonicalPath
        """

        def out = executer.run().output
        assert out.contains("javaHome=" + Jvm.current().javaHome.canonicalPath)

        out = executer.withJavaHome(alternateJavaHome).run().output
        assert out.contains("javaHome=" + alternateJavaHome.canonicalPath)
    }
}
