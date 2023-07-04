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

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.internal.jvm.Jvm
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions
import org.gradle.util.internal.TextUtil
import spock.lang.IgnoreIf
import spock.lang.Issue

class BuildEnvironmentIntegrationTest extends AbstractIntegrationSpec {
    def "canonicalizes working directory"() {
        given:
        testProject()

        when:
        def relativeDir = new File(testDirectory, 'java/multiproject/../quickstart')
        executer.inDirectory(relativeDir).run()

        then:
        noExceptionThrown()
    }

    @Requires(UnitTestPreconditions.CaseInsensitiveFs)
    def "canonicalizes working directory on case insensitive file system"() {
        testProject()

        when:
        def mixedCaseDir = new File(testDirectory, "JAVA/QuickStart")
        executer.inDirectory(mixedCaseDir).run()

        then:
        noExceptionThrown()
    }

    @Requires(UnitTestPreconditions.Windows)
    def "canonicalizes working directory for short windows path"() {
        testProject()

        when:
        def shortDir = new File(testDirectory, 'java/QUICKS~1')
        executer.inDirectory(shortDir).run()

        then:
        noExceptionThrown()
    }

    private testProject() {
        file("java/multiproject").createDir()
        def projectDir = file("java/quickstart")
        projectDir.file('build.gradle') << "assert file('.') == new File(new URI('${projectDir.toURI()}'))"
    }

    @Issue("GRADLE-1762")
    def "build uses environment variables from where the build was launched"() {
        file('build.gradle') << "println providers.environmentVariable('foo').orNull"

        when:
        def out = executer.withEnvironmentVars(foo: "gradle rocks!").run().output

        then:
        out.contains("gradle rocks!")

        when:
        out = executer.withEnvironmentVars(foo: "and will be even better").run().output

        then:
        out.contains("and will be even better")
    }

    @Requires(UnitTestPreconditions.WorkingDir)
    def "build is executed with working directory set to where the build was launched from"() {
        def project1 = file("project1")
        def project2 = file("project2")

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
        file('build.gradle') << """
            println('prop1=' + providers.systemProperty('prop1').get())
            task show {
                doLast {
                    println('prop1=' + System.getProperty('prop1'))
                }
            }
        """
        if (!GradleContextualExecuter.configCache) {
            buildFile << """
                println('prop2=' + System.getProperty('prop2'))
            """
        } else {
            buildFile << """
                println('prop2=' + providers.systemProperty('prop2').orNull)
            """
        }

        when:
        executer.withArguments("-Dprop1=some-value")
        run("show")

        then:
        output.count("prop1=some-value") == 2
        outputContains("prop2=null")

        when:
        executer.withArguments("-Dprop1=new-value", "-Dprop2=other-value")
        run("show")

        then:
        output.count("prop1=new-value") == 2
        outputContains("prop2=other-value")
    }

    @IgnoreIf({ AvailableJavaHomes.differentJdk == null })
    def "java home from environment should be used to run build"() {
        def alternateJavaHome = AvailableJavaHomes.differentJdk.javaHome

        buildFile << """
            task printJavaHome {
                doLast {
                    println 'javaHome=' + org.gradle.internal.jvm.Jvm.current().javaHome.canonicalPath
                }
            }
        """

        when:
        def out = executer.withTasks('printJavaHome').run().output

        then:
        out.contains("javaHome=" + Jvm.current().javaHome.canonicalPath)

        when:
        out = executer.withJavaHome(alternateJavaHome).withTasks('printJavaHome').run().output

        then:
        out.contains("javaHome=" + alternateJavaHome.canonicalPath)
    }

    @IgnoreIf({ AvailableJavaHomes.differentJdk == null })
    def "java home from gradle properties should be used to run build"() {
        def alternateJavaHome = AvailableJavaHomes.differentJdk.javaHome

        file('gradle.properties') << """
org.gradle.java.home=${TextUtil.escapeString(alternateJavaHome.canonicalPath)}
"""
        file('build.gradle') << "println 'javaHome=' + org.gradle.internal.jvm.Jvm.current().javaHome.absolutePath"

        when:
        def out = executer.useOnlyRequestedJvmOpts().run().output

        then:
        out.contains("javaHome=" + alternateJavaHome.canonicalPath)
    }

    def "jvm args from gradle properties should be used to run build"() {
        file('gradle.properties') << "org.gradle.jvmargs=-Xmx52m -Dprop1=some-value"

        file('build.gradle') << """
            assert java.lang.management.ManagementFactory.runtimeMXBean.inputArguments.contains('-Xmx52m')
            println('prop1=' + providers.systemProperty('prop1').get())
            task show {
                doLast {
                    println('prop1=' + System.getProperty('prop1'))
                }
            }
        """
        if (!GradleContextualExecuter.configCache) {
            buildFile << """
                println('prop2=' + System.getProperty('prop2'))
            """
        } else {
            buildFile << """
                println('prop2=' + providers.systemProperty('prop2').orNull)
            """
        }

        when:
        executer.useOnlyRequestedJvmOpts()
        run("show")

        then:
        output.count("prop1=some-value") == 2
        outputContains("prop2=null")

        when:
        file('gradle.properties').text = "org.gradle.jvmargs=-Xmx52m -Dprop1=new-value -Dprop2=other-value"
        executer.useOnlyRequestedJvmOpts()
        run("show")

        then:
        output.count("prop1=new-value") == 2
        outputContains("prop2=other-value")
    }
}
