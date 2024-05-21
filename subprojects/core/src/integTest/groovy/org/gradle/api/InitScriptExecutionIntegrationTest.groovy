/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.api

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.ToBeFixedForIsolatedProjects
import org.gradle.integtests.fixtures.UnsupportedWithConfigurationCache
import org.gradle.integtests.fixtures.executer.ArtifactBuilder
import org.gradle.integtests.fixtures.timeout.IntegrationTestTimeout
import org.gradle.test.fixtures.file.TestFile
import spock.lang.Issue

@IntegrationTestTimeout(300)
class InitScriptExecutionIntegrationTest extends AbstractIntegrationSpec {
    def "executes init.gradle from user home dir"() {
        given:
        executer.requireOwnGradleUserHomeDir()

        and:
        executer.gradleUserHomeDir.file('init.gradle') << 'println "greetings from user home"'

        when:
        run()

        then:
        output.contains("greetings from user home")
    }

    def "executes init scripts from init.d directory in user home dir in alphabetical order"() {
        given:
        executer.requireOwnGradleUserHomeDir()

        and:
        executer.gradleUserHomeDir.file('init.d/a.gradle') << 'println "init #a#"'
        executer.gradleUserHomeDir.file('init.d/b.gradle') << 'println "init #b#"'
        executer.gradleUserHomeDir.file('init.d/c.gradle') << 'println "init #c#"'

        when:
        run()

        then:
        def a = output.indexOf('init #a#')
        def b = output.indexOf('init #b#')
        def c = output.indexOf('init #c#')
        a < b
        b < c
    }

    def "executes init script with correct environment"() {
        given:
        def implClassName = 'com.google.common.collect.Multimap'
        createExternalJar()

        and:
        TestFile initScript = file('init.gradle')
        initScript << """
initscript {
    dependencies { classpath files('repo/test-1.3.jar') }
}
new org.gradle.test.BuildClass()
println 'quiet message'
logging.captureStandardOutput(LogLevel.ERROR)
println 'error message'
assert gradle != null
assert initscript.classLoader == getClass().classLoader.parent
assert initscript.classLoader == Thread.currentThread().contextClassLoader
Gradle.class.classLoader.loadClass('${implClassName}')
try {
    initscript.classLoader.loadClass('${implClassName}')
    assert false: 'should fail'
} catch (ClassNotFoundException e) {
    // expected
} finally {
    if (initscript.classLoader instanceof Closeable) {
        initscript.classLoader.close()
    }
}
"""

        and:
        buildFile << 'task doStuff'

        when:
        result = executer.usingInitScript(initScript).withTasks('doStuff').run()

        then:
        outputContains('quiet message')
        result.assertHasErrorOutput('error message')
    }

    def "each init script has independent ClassLoader"() {
        given:
        createExternalJar()

        and:
        TestFile initScript1 = file('init1.gradle')
        initScript1 << '''
initscript {
    dependencies { classpath files('repo/test-1.3.jar') }
}
new org.gradle.test.BuildClass()
'''
        TestFile initScript2 = file('init2.gradle')
        initScript2 << '''
try {
    Class.forName('org.gradle.test.BuildClass')
    fail()
} catch (ClassNotFoundException e) {
}
'''

        buildFile << 'task doStuff'

        when:
        executer.usingInitScript(initScript1).usingInitScript(initScript2).withTasks('doStuff').run()

        then:
        notThrown(Throwable)
    }

    def "each Kotlin init script has independent ClassLoader"() {
        given:
        createExternalJar()

        and:
        TestFile initScript1 = file('init1.init.gradle.kts')
        initScript1 << '''
initscript {
    dependencies { classpath(files("repo/test-1.3.jar")) }
}
org.gradle.test.BuildClass()
'''
        TestFile initScript2 = file('init2.init.gradle.kts')
        initScript2 << '''
try {
    Class.forName("org.gradle.test.BuildClass")
} catch (e: ClassNotFoundException) {
    println("BuildClass not found as expected.")
}
'''

        buildFile << 'task doStuff'

        when:
        result = executer.usingInitScript(initScript1).usingInitScript(initScript2).withTasks('doStuff').run()

        then:
        notThrown(Throwable)

        and:
        outputContains("BuildClass not found as expected")
    }

    def "executes Kotlin init scripts from init.d directory in user home dir in alphabetical order"() {
        given:
        executer.requireOwnGradleUserHomeDir()

        and:
        ["c", "b", "a"].each {
            executer.gradleUserHomeDir.file("init.d/${it}.gradle.kts") << """
                // make sure the script is evaluated as Kotlin by explicitly qualifying `println`
                kotlin.io.println("init #${it}#")
            """
        }

        when:
        run()

        then:
        def a = output.indexOf('init #a#')
        def b = output.indexOf('init #b#')
        def c = output.indexOf('init #c#')
        a >= 0
        b > a
        c > b
    }

    @ToBeFixedForIsolatedProjects(because = "allprojects")
    def "init script can inject configuration into the root project and all projects"() {
        given:
        createDirs("a", "b")
        settingsFile << "include 'a', 'b'"

        and:
        file("init.gradle") << """
allprojects {
    task worker
}
rootProject {
    task root(dependsOn: allprojects*.worker)
}
        """

        when:
        executer.withArguments("-I", "init.gradle")
        run "root"

        then:
        result.assertTasksExecuted(':worker', ':a:worker', ':b:worker', ':root')
    }

    def "notices changes to init scripts that do not change the file length"() {
        def initScript = file("init.gradle")
        initScript.text = "println 'counter: __'"
        long before = initScript.length()

        expect:
        (10..20).each {
            initScript.text = "println 'counter: $it'"
            assert initScript.length() == before

            executer.withArguments("--init-script", initScript.absolutePath)
            succeeds()
            result.assertOutputContains("counter: $it")
        }
    }

    def 'init script classpath configuration has proper usage attribute'() {
        def initScript = file('init.gradle')
        initScript << """
initscript {
    configurations.classpath {
        def value = attributes.getAttribute(Usage.USAGE_ATTRIBUTE)
        assert value.name == Usage.JAVA_RUNTIME
    }
}
"""
        expect:
        executer.withArguments('--init-script', initScript.absolutePath)
        succeeds()
    }

    @Issue("https://github.com/gradle/gradle-native/issues/962")
    @UnsupportedWithConfigurationCache
    def "init script can register all projects hook from within the projects loaded callback of build listener"() {
        given:
        executer.requireOwnGradleUserHomeDir()

        and:
        file("buildSrc/settings.gradle").createFile()

        and:
        executer.gradleUserHomeDir.file('init.d/a.gradle') << '''
            gradle.addListener(new BuildAdapter() {
                void projectsLoaded(Gradle gradle) {
                    gradle.rootProject.allprojects {
                        println "Project '$name'"
                    }
                }
            })
        '''

        and:
        settingsFile << "rootProject.name = 'root'"

        when:
        succeeds()

        then:
        output.contains("Project 'buildSrc'")
        output.contains("Project 'root'")
    }

    @Issue("https://github.com/gradle/gradle/issues/17555")
    def "init script file is a dotfile"() {
        def initScript = file('.empty')
        initScript << 'println "greetings from empty init script"'
        executer.withArguments('--init-script', initScript.absolutePath)

        when:
        run()

        then:
        output.contains("greetings from empty init script")
    }

    private def createExternalJar() {
        ArtifactBuilder builder = artifactBuilder()
        builder.sourceFile('org/gradle/test/BuildClass.java') << '''
            package org.gradle.test;
            public class BuildClass { }
'''
        builder.buildJar(file("repo/test-1.3.jar"))
    }
}
