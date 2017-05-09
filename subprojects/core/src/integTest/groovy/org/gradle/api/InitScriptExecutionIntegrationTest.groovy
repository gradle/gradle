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
import org.gradle.integtests.fixtures.executer.ArtifactBuilder
import org.gradle.integtests.fixtures.executer.ExecutionResult
import org.gradle.test.fixtures.file.TestFile

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
        createExternalJar();

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
        ExecutionResult result = executer.usingInitScript(initScript).withTasks('doStuff').run()

        then:
        result.output.contains('quiet message')
        !result.output.contains('error message')
        result.error.contains('error message')
        !result.error.contains('quiet message')
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
        executer.usingInitScript(initScript1).usingInitScript(initScript2)

        then:
        notThrown(Throwable)
    }

    def "init script can inject configuration into the root project and all projects"() {
        given:
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
        int before = initScript.length()

        expect:
        (10..40).each {
            initScript.text = "println 'counter: $it'"
            assert initScript.length() == before

            executer.withArguments("--init-script", initScript.absolutePath)
            succeeds()
            result.assertOutputContains("counter: $it")
        }
    }

    private def createExternalJar() {
        ArtifactBuilder builder = artifactBuilder();
        builder.sourceFile('org/gradle/test/BuildClass.java') << '''
            package org.gradle.test;
            public class BuildClass { }
'''
        builder.buildJar(file("repo/test-1.3.jar"))
    }
}
